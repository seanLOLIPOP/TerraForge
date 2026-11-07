import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Shared, bounded CPU workers. Each task owns its output rows; no shared random generators. */
final class CpuWork {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static int parallelism(){int maximum=Math.max(1,Runtime.getRuntime().availableProcessors()-1);return Math.max(1,Math.min(maximum,Integer.getInteger("terraforge.cpuThreads",maximum)));}
    private static final ForkJoinPool POOL=new ForkJoinPool(parallelism(),
        pool->{var thread=ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);thread.setName("TerraForge-CPU-"+IDS.incrementAndGet());return thread;},null,false);
    private static final ForkJoinPool ENCODERS=new ForkJoinPool(Math.max(1,Math.min(Math.min(16,parallelism()),Integer.getInteger("terraforge.chunkThreads",8))),
        pool->{var thread=ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);thread.setName("TerraForge-chunks-"+IDS.incrementAndGet());return thread;},null,false);
    private static final ThreadLocal<Boolean> SERIAL_ROWS=ThreadLocal.withInitial(()->false);
    static int threads(){return POOL.getParallelism();}
    static int batchThreads(){return ENCODERS.getParallelism();}
    interface RegionJob {void run()throws Exception;}
    interface RegionConsumer {void accept(int index)throws Exception;}
    /** Region workers already run independent maps in parallel: do not multiply their thread counts. */
    static void region(RegionJob job)throws Exception{boolean previous=SERIAL_ROWS.get();SERIAL_ROWS.set(true);try{job.run();}finally{SERIAL_ROWS.set(previous);}}
    /** Drain workers before callers restore shared generation state, including cancellation and failure. */
    static void regions(int count,int threads,BooleanSupplier stop,RegionConsumer job)throws Exception{
        var pool=Executors.newFixedThreadPool(Math.min(count,threads),r->{var thread=new Thread(r,"TerraForge-region-"+IDS.incrementAndGet());thread.setDaemon(true);return thread;});
        var jobs=new java.util.ArrayList<Future<?>>();boolean interrupted=false;
        try{
            for(int i=0;i<count;i++){int index=i;jobs.add(pool.submit(()->{if(stop.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException();region(()->job.accept(index));return null;}));}
            pool.shutdown();for(var task:jobs)task.get();
        }catch(InterruptedException error){interrupted=true;throw new CancellationException();}
        catch(ExecutionException error){if(error.getCause() instanceof Exception cause)throw cause;throw new IllegalStateException(error.getCause());}
        finally{
            pool.shutdownNow();while(!pool.isTerminated())try{pool.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException error){interrupted=true;}
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
    static void rows(int rows,int width,BooleanSupplier stop,IntConsumer body){
        rows(rows,width,stop,body,false);
    }
    /** Bounded chunk batches share the CPU pool even inside a memory-limited region job. */
    static void batch(int count,BooleanSupplier stop,IntConsumer body){rows(count,1,stop,body,true);}
    private static void rows(int rows,int width,BooleanSupplier stop,IntConsumer body,boolean batch){
        Thread owner=Thread.currentThread();var pool=batch?ENCODERS:POOL;int threads=pool.getParallelism();
        if(threads==1||!batch&&(rows*(long)width<262144||SERIAL_ROWS.get())){
            for(int row=0;row<rows;row++){if(owner.isInterrupted()||stop.getAsBoolean())throw new CancellationException();body.accept(row);}return;
        }
        AtomicBoolean interrupted=new AtomicBoolean();AtomicReference<Throwable> failure=new AtomicReference<>();
        int grain=batch?1:Math.max(8,(rows+threads*2-1)/(threads*2));
        class Task extends RecursiveAction {
            final int first,last;
            Task(int first,int last){this.first=first;this.last=last;}
            protected void compute(){
                if(failure.get()!=null||interrupted.get())return;
                if(last-first>grain){int middle=(first+last)/2;invokeAll(new Task(first,middle),new Task(middle,last));return;}
                try{for(int row=first;row<last;row++){
                    if(failure.get()!=null)return;
                    if(interrupted.get()||owner.isInterrupted()||stop.getAsBoolean())throw new CancellationException();
                    body.accept(row);
                }}catch(Throwable error){failure.compareAndSet(null,error);}
            }
        }
        var task=pool.submit(new Task(0,rows));
        try{task.get();}
        catch(InterruptedException error){interrupted.set(true);task.join();Thread.currentThread().interrupt();throw new CancellationException();}
        catch(ExecutionException error){throw new IllegalStateException("CPU generation failed",error.getCause());}
        Throwable error=failure.get();
        if(error instanceof RuntimeException runtime)throw runtime;
        if(error instanceof Error fatal)throw fatal;
        if(error!=null)throw new IllegalStateException(error);
    }
}
