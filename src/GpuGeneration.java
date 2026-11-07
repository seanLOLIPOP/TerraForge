import java.nio.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.*;
import org.lwjgl.system.*;
import static org.lwjgl.opencl.CL10.*;

/** Optional vendor-neutral OpenCL source nodes. One bounded queue, independent of viewer contexts. */
final class GpuGeneration {
    private static volatile Backend backend;
    private static volatile boolean attempted;
    private static final java.util.concurrent.atomic.AtomicLong JOBS=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong UPLOADS=new java.util.concurrent.atomic.AtomicLong(),DOWNLOADS=new java.util.concurrent.atomic.AtomicLong(),FUSED=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong RESIDENT_BYTES=new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<Session> SESSION=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CPU_ONLY=ThreadLocal.withInitial(()->false);
    private static volatile String status="GPU not initialized";
    static String status(){return status;}
    static long jobs(){return JOBS.get();}
    static long uploadedBytes(){return UPLOADS.get();}static long downloadedBytes(){return DOWNLOADS.get();}static long fusedNodes(){return FUSED.get();}
    static long residentBytes(){return RESIDENT_BYTES.get();}
    static void recordFused(int count){FUSED.addAndGet(count);}
    static boolean enabled(){return !CPU_ONLY.get()&&!System.getProperty("terraforge.gpu","auto").equalsIgnoreCase("cpu");}
    static boolean deferring(){Session s=SESSION.get();return s!=null&&s.deferred;}
    static boolean fusing(){Session s=SESSION.get();return s!=null&&!s.retainFields&&s.active&&enabled()&&(!attempted||backend!=null);}
    static Session begin(int n,boolean retainFields,BooleanSupplier stop){return new Session(n,retainFields,stop);}
    static final class RetryCpu extends RuntimeException {RetryCpu(Throwable cause){super(cause);}}
    static final class CpuOnly implements AutoCloseable {final boolean previous=CPU_ONLY.get();CpuOnly(){CPU_ONLY.set(true);}public void close(){CPU_ONLY.set(previous);}}
    static boolean graphPass(java.util.function.BooleanSupplier job){Session s=SESSION.get();boolean previous=s!=null&&s.deferred;if(s!=null)s.deferred=s.active&&enabled();try{return job.getAsBoolean();}finally{if(s!=null)s.deferred=previous;}}
    static float[] fusedPass(java.util.function.Supplier<float[]> job){Session s=SESSION.get();boolean previous=s!=null&&s.deferred;if(s!=null)s.deferred=s.active&&enabled();try{return job.get();}finally{if(s!=null)s.deferred=previous;}}
    static synchronized void cpuBarrier(){Session s=SESSION.get();if(s!=null)s.flush();}
    static synchronized void cpuBarrier(float[][] inputs,EnvironmentMap environment){Session s=SESSION.get();if(s!=null)s.prepareCpu(inputs,environment);}
    static synchronized void materialize(float[] input){Session s=SESSION.get();if(s!=null)s.read(input);}
    private static final class BudgetExceeded extends RuntimeException {}
    private record Buffer(long handle,long bytes){}
    private static final class Field {final Buffer buffer;boolean dirty;Field(Buffer buffer,boolean dirty){this.buffer=buffer;this.dirty=dirty;}}
    /** A graph owns its resident arrays. No data survives evaluation or leaks into another seed/build. */
    static final class Session implements AutoCloseable {
        final Session previous=SESSION.get();final boolean retainFields,active;final BooleanSupplier stop;
        final java.util.IdentityHashMap<float[],Field> fields=new java.util.IdentityHashMap<>();final java.util.ArrayList<Buffer> parameters=new java.util.ArrayList<>();Backend owner;Buffer dummy;boolean deferred;
        Session(int n,boolean retainFields,BooleanSupplier stop){this.retainFields=retainFields;this.stop=stop;active=n>=512&&enabled()&&!System.getProperty("terraforge.gpuPipeline","true").equalsIgnoreCase("false");SESSION.set(this);}
        Buffer allocate(long bytes){if(owner.liveBytes+bytes>owner.budget)throw new BudgetExceeded();try(MemoryStack stack=MemoryStack.stackPush()){
            IntBuffer error=stack.mallocInt(1);long handle=clCreateBuffer(owner.context,CL_MEM_READ_WRITE,Math.max(4,bytes),error);ok(error.get(0));owner.liveBytes+=Math.max(4,bytes);RESIDENT_BYTES.addAndGet(Math.max(4,bytes));return new Buffer(handle,Math.max(4,bytes));}}
        void release(Buffer buffer){if(buffer!=null){clReleaseMemObject(buffer.handle);owner.liveBytes-=buffer.bytes;RESIDENT_BYTES.addAndGet(-buffer.bytes);}}
        void select(Backend b){if(owner!=null&&owner!=b&&!fields.isEmpty())throw new RetryCpu(new IllegalStateException("GPU backend changed during evaluation"));owner=b;}
        long input(float[] data){Field existing=fields.get(data);if(existing!=null)return existing.buffer.handle;
            Buffer memory=allocate(data.length*4L);try{ok(clEnqueueWriteBuffer(owner.queue,memory.handle,true,0,data,null,null));UPLOADS.addAndGet(data.length*4L);fields.put(data,new Field(memory,false));return memory.handle;}catch(RuntimeException error){release(memory);throw error;}}
        Field output(float[] data){Buffer memory=allocate(data.length*4L);var field=new Field(memory,false);fields.put(data,field);return field;}
        long dummy(){if(dummy==null)dummy=allocate(4);return dummy.handle;}
        void read(float[] data){Field field=fields.get(data);if(field==null||!field.dirty)return;checkStop(stop);
            if(owner.queue==0)throw new RetryCpu(new IllegalStateException("GPU queue unavailable"));
            try{ok(clEnqueueReadBuffer(owner.queue,field.buffer.handle,true,0,data,null,null));DOWNLOADS.addAndGet(data.length*4L);field.dirty=false;}catch(RuntimeException error){failed(error);throw new RetryCpu(error);}checkStop(stop);}
        void flush(){for(float[] data:fields.keySet())read(data);clear();}
        void prepareCpu(float[][] inputs,EnvironmentMap environment){
            var needed=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<float[],Boolean>());java.util.Collections.addAll(needed,inputs);
            if(environment!=null)java.util.Collections.addAll(needed,environment.height,environment.water,environment.snow,environment.ice,environment.temperature,environment.rain,environment.river,environment.glacier,environment.zoneSource,environment.zoneSlope,environment.zoneNorth,environment.zoneBowl,environment.riverGrad,environment.moisture,environment.shoreDistance);
            needed.remove(null);for(float[] input:needed)read(input);
            if(owner!=null&&owner.queue!=0)clFinish(owner.queue);
            for(float[] input:needed){Field field=fields.remove(input);if(field!=null)release(field.buffer);}
        }
        void finish(float[] result,EnvironmentMap environment){synchronized(GpuGeneration.class){
            if(retainFields){flush();return;}read(result);
            for(float[] channel:new float[][]{environment.height,environment.water,environment.snow,environment.ice,environment.temperature,environment.rain,environment.river,environment.glacier})if(channel!=null)read(channel);
        }}
        void clear(){if(owner!=null&&owner.queue!=0)clFinish(owner.queue);for(Field field:fields.values())release(field.buffer);fields.clear();for(Buffer buffer:parameters)release(buffer);parameters.clear();release(dummy);dummy=null;}
        public void close(){synchronized(GpuGeneration.class){try{clear();}finally{SESSION.set(previous);}}}
    }
    private static Backend backend(){if(!attempted){attempted=true;backend=new Backend();status="OpenCL · "+backend.name;}return backend;}
    private static void failed(Throwable error){if(backend!=null){backend.close();backend=null;}String message=String.valueOf(error.getMessage());String brief=message.lines().findFirst().orElse(error.getClass().getSimpleName());status="CPU fallback · "+brief.substring(0,Math.min(240,brief.length()));System.err.println("Terrain generation: CPU fallback · "+message);Session s=SESSION.get();if(s!=null&&!s.fields.isEmpty())throw new RetryCpu(error);}
    /** All calls are serialized; region workers never create competing GPU contexts. */
    static synchronized boolean compute(String key,String body,double[] params,float[][] inputs,float[] output,int n,int size,int sea,long seed,BooleanSupplier stop){
        long bytes=output.length*8L;for(float[] input:inputs)if(input!=null)bytes+=input.length*4L;
        if(!enabled()||n<512||bytes>(256L<<20))return false;
        checkStop(stop);
        try{Backend b=backend();if(b==null||bytes>b.budget)return false;
            if(deferring()){Session s=SESSION.get();s.select(b);try{b.resident(s,key,body,params,inputs,output,n,size,sea,seed,stop);}catch(BudgetExceeded limit){s.flush();try{b.resident(s,key,body,params,inputs,output,n,size,sea,seed,stop);}catch(BudgetExceeded exhausted){s.flush();return false;}}}
            else{if(bytes+b.liveBytes>b.budget){Session s=SESSION.get();if(s!=null)s.flush();if(bytes+b.liveBytes>b.budget)return false;}b.compute(key,body,params,inputs,output,n,size,sea,seed,stop);}
            JOBS.incrementAndGet();return true;}
        catch(CancellationException cancelled){throw cancelled;}
        catch(RetryCpu retry){throw retry;}
        catch(LinkageError|RuntimeException error){failed(error);return false;}
    }
    static synchronized boolean thermalGraph(float[] input,float[] output,int n,int iterations,BooleanSupplier stop){
        if(!deferring()||iterations<2||input.length*16L>(256L<<20))return false;checkStop(stop);
        try{Backend b=backend();if(b==null)return false;Session s=SESSION.get();s.select(b);
            try{b.residentThermal(s,input,output,n,iterations,stop);}catch(BudgetExceeded limit){s.flush();try{b.residentThermal(s,input,output,n,iterations,stop);}catch(BudgetExceeded exhausted){s.flush();return false;}}
            JOBS.incrementAndGet();return true;
        }catch(CancellationException cancelled){throw cancelled;}catch(RetryCpu retry){throw retry;}catch(LinkageError|RuntimeException error){failed(error);return false;}
    }
    static synchronized boolean thermal(float[] height,int n,int iterations,BooleanSupplier stop){
        if(!enabled()||n<512||iterations<2||height.length*13L>(256L<<20))return false;
        checkStop(stop);
        try{Backend b=backend();if(b==null||height.length*13L+b.liveBytes>b.budget)return false;b.thermal(height,n,iterations,stop);JOBS.incrementAndGet();return true;}
        catch(CancellationException cancelled){throw cancelled;}
        catch(LinkageError|RuntimeException error){failed(error);return false;}
    }
    static void checkStop(BooleanSupplier stop){if(Thread.currentThread().isInterrupted()||stop.getAsBoolean())throw new CancellationException();}
    static synchronized boolean generate(TerrainGraph.Node node,int n,int sea,long seed,float[] output,BooleanSupplier stop){
        if(!enabled()||n<512||(node.type!=TerrainGraph.Type.NOISE&&node.type!=TerrainGraph.Type.RIDGES))return false;
        checkStop(stop);
        try {
            backend();
            if(backend==null)return false;
            if(deferring()&&GpuNodes.apply(node,new float[0][],output,n,n,sea,seed,null,stop))return true;
            if(backend==null)return false;
            if(backend.liveBytes+(4L<<20)>backend.budget)return false;
            backend.generate(node,n,sea,seed,output,stop);JOBS.incrementAndGet();return true;
        }catch(CancellationException cancelled){throw cancelled;}
        catch(RetryCpu retry){throw retry;}
        catch(LinkageError|RuntimeException error){
            failed(error);return false;
        }
    }
    private static void ok(int code){if(code!=CL_SUCCESS)throw new IllegalStateException("OpenCL error "+code);}
    private static final class Backend {
        long context,queue,program,kernel,device,budget,liveBytes;String name;
        final java.util.Map<String,long[]> compiled=new java.util.LinkedHashMap<>();
        Backend(){
            try(MemoryStack stack=MemoryStack.stackPush()){
                CL.getFunctionProvider();IntBuffer count=stack.mallocInt(1);ok(clGetPlatformIDs(null,count));
                PointerBuffer platforms=stack.mallocPointer(count.get(0));ok(clGetPlatformIDs(platforms,(IntBuffer)null));
                long chosen=0,platform=0,bestMemory=-1;
                for(int p=0;p<platforms.capacity();p++){
                    long candidatePlatform=platforms.get(p);CL.createPlatformCapabilities(candidatePlatform);
                    int result=clGetDeviceIDs(candidatePlatform,CL_DEVICE_TYPE_GPU,null,count);
                    if(result==CL_DEVICE_NOT_FOUND)continue;ok(result);
                    PointerBuffer devices=stack.mallocPointer(count.get(0));ok(clGetDeviceIDs(candidatePlatform,CL_DEVICE_TYPE_GPU,devices,(IntBuffer)null));
                    for(int d=0;d<devices.capacity();d++){
                        long device=devices.get(d);var caps=CL.createDeviceCapabilities(device,CL.createPlatformCapabilities(candidatePlatform));
                        if(!caps.cl_khr_fp64||!caps.OpenCL12)continue;
                        String filter=System.getProperty("terraforge.gpuDevice","").toLowerCase(java.util.Locale.ROOT);
                        if(!filter.isEmpty()){ByteBuffer deviceName=stack.malloc(1024);ok(clGetDeviceInfo(device,CL_DEVICE_NAME,deviceName,null));if(!MemoryUtil.memUTF8(MemoryUtil.memAddress(deviceName)).toLowerCase(java.util.Locale.ROOT).contains(filter))continue;}
                        LongBuffer memory=stack.mallocLong(1);ok(clGetDeviceInfo(device,CL_DEVICE_GLOBAL_MEM_SIZE,memory,null));
                        if(memory.get(0)>bestMemory){chosen=device;platform=candidatePlatform;bestMemory=memory.get(0);}
                    }
                }
                if(chosen==0)throw new IllegalStateException("no OpenCL GPU with double precision available");
                device=chosen;
                budget=Math.min(Math.min(256L<<20,bestMemory/4),Math.max(8,Math.min(256,Integer.getInteger("terraforge.gpuBudgetMiB",256)))*1024L*1024);
                ByteBuffer text=stack.malloc(1024);ok(clGetDeviceInfo(chosen,CL_DEVICE_NAME,text,null));name=MemoryUtil.memUTF8(MemoryUtil.memAddress(text));
                IntBuffer error=stack.mallocInt(1);
                context=clCreateContext(stack.pointers(CL_CONTEXT_PLATFORM,platform,0),stack.pointers(chosen),null,0,error);ok(error.get(0));
                queue=clCreateCommandQueue(context,chosen,0,error);ok(error.get(0));
                program=clCreateProgramWithSource(context,SOURCE,error);ok(error.get(0));
                int build=clBuildProgram(program,chosen,"-cl-std=CL1.2",null,0);
                if(build!=CL_SUCCESS){ByteBuffer log=stack.malloc(8192);clGetProgramBuildInfo(program,chosen,CL_PROGRAM_BUILD_LOG,log,null);throw new IllegalStateException("GPU kernel compilation: "+MemoryUtil.memUTF8(MemoryUtil.memAddress(log)));}
                kernel=clCreateKernel(program,"sourceNode",error);ok(error.get(0));
            }catch(RuntimeException|LinkageError error){close();throw error;}
        }
        void generate(TerrainGraph.Node node,int n,int sea,long seed,float[] output,BooleanSupplier stop){
            // At most 4 MiB of device output and host staging, regardless of map size.
            int capacity=Math.min(output.length,1024*1024);long buffer=0;FloatBuffer host=MemoryUtil.memAllocFloat(capacity);
            try(MemoryStack stack=MemoryStack.stackPush()){
                IntBuffer error=stack.mallocInt(1);buffer=clCreateBuffer(context,CL_MEM_WRITE_ONLY,capacity*4L,error);ok(error.get(0));
                boolean ridges=node.type==TerrainGraph.Type.RIDGES;
                ok(clSetKernelArg1p(kernel,0,buffer));ok(clSetKernelArg1i(kernel,1,n));ok(clSetKernelArg1i(kernel,4,sea));
                ok(clSetKernelArg1l(kernel,5,seed+(long)node.p("seed")));ok(clSetKernelArg1d(kernel,6,node.p("scale")));
                ok(clSetKernelArg1i(kernel,7,ridges?5:(int)node.p("detail")));ok(clSetKernelArg1d(kernel,8,ridges?.5:node.p("rough")));
                ok(clSetKernelArg1d(kernel,9,node.p("height")));ok(clSetKernelArg1d(kernel,10,ridges?node.p("sharp"):0));
                for(int offset=0;offset<output.length;offset+=capacity){
                    checkStop(stop);int count=Math.min(capacity,output.length-offset);
                    ok(clSetKernelArg1i(kernel,2,offset));ok(clSetKernelArg1i(kernel,3,count));
                    ok(clEnqueueNDRangeKernel(queue,kernel,1,null,stack.pointers(count),null,null,null));
                    host.clear().limit(count);ok(clEnqueueReadBuffer(queue,buffer,true,0,host,null,null));checkStop(stop);host.get(output,offset,count);DOWNLOADS.addAndGet(count*4L);
                }
            }finally{if(buffer!=0){clFinish(queue);clReleaseMemObject(buffer);}MemoryUtil.memFree(host);}
        }
        long compile(String key,String source,String function){
            long[] existing=compiled.get(key);if(existing!=null){compiled.remove(key);compiled.put(key,existing);return existing[1];}
            try(MemoryStack stack=MemoryStack.stackPush()){
                IntBuffer error=stack.mallocInt(1);long p=clCreateProgramWithSource(context,source,error);ok(error.get(0));long k=0;
                try{
                    int result=clBuildProgram(p,device,"-cl-std=CL1.2",null,0);
                    if(result!=CL_SUCCESS){PointerBuffer length=stack.mallocPointer(1);ok(clGetProgramBuildInfo(p,device,CL_PROGRAM_BUILD_LOG,(ByteBuffer)null,length));ByteBuffer log=MemoryUtil.memAlloc((int)length.get(0));try{ok(clGetProgramBuildInfo(p,device,CL_PROGRAM_BUILD_LOG,log,null));throw new IllegalStateException(key+": "+MemoryUtil.memUTF8(MemoryUtil.memAddress(log)));}finally{MemoryUtil.memFree(log);}}
                    k=clCreateKernel(p,function,error);ok(error.get(0));
                    if(compiled.size()>=64){var iterator=compiled.entrySet().iterator();long[] oldest=iterator.next().getValue();clReleaseKernel(oldest[1]);clReleaseProgram(oldest[0]);iterator.remove();}
                    compiled.put(key,new long[]{p,k});return k;
                }catch(RuntimeException failure){if(k!=0)clReleaseKernel(k);clReleaseProgram(p);throw failure;}
            }
        }
        long upload(float[] data){try(MemoryStack stack=MemoryStack.stackPush()){
            IntBuffer error=stack.mallocInt(1);long buffer=clCreateBuffer(context,CL_MEM_READ_WRITE,Math.max(4,data==null?4:data.length*4L),error);ok(error.get(0));
            try{if(data!=null){ok(clEnqueueWriteBuffer(queue,buffer,true,0,data,null,null));UPLOADS.addAndGet(data.length*4L);}return buffer;}catch(RuntimeException e){clReleaseMemObject(buffer);throw e;}
        }}
        String mappingSource(String key,String body,boolean resident){
            String source=SOURCE+"\n double fclamp(double v,double lo,double hi){return (float)clamp(v,lo,hi); }\n"+
                "double fractal(double x,double z,ulong seed,int octaves,double persistence){double value=0,amp=1,total=0;for(int o=0;o<octaves;o++){value+=sampleNoise(x,z,seed+(ulong)(o*71))*amp;total+=amp;amp*=persistence;x*=2.0;z*=2.0;}return value/total;}\n"+
                (key.startsWith("MOUNTAIN:")?GpuMountains.HELPERS:"")+(key.startsWith("ORGANIC_MATERIALS:")?GpuMaterials.helpers()+GpuZones.HELPERS:"");
            return source+"kernel void mapNode(global float* out,global const float* A,global const float* B,global const float* C,global const double* params,int n,int size,int sea,ulong seed,int offset,int batchCount"+(resident?",int flags":"")+") {int j=get_global_id(0);if(j>=batchCount)return;int oi=j+offset,channel=oi/(n*n),i=oi%(n*n),x=i%n,z=i/n;double a="+(resident?"(flags&1)?A[i]:0":"A[i]")+",b="+(resident?"(flags&2)?B[i]:0":"B[i]")+",c="+(resident?"(flags&4)?C[i]:0":"C[i]")+",value=a;"+body+(resident?"out[oi]":"out[j]")+"=(float)value;}";
        }
        void resident(Session session,String key,String body,double[] params,float[][] inputs,float[] output,int n,int size,int sea,long seed,BooleanSupplier stop){
            long k=compile("resident:"+key,mappingSource(key,body,true),"mapNode");
            try(MemoryStack stack=MemoryStack.stackPush()){
                long[] arguments=new long[5];int flags=0;
                for(int index=0;index<3;index++){
                    float[] input=index<inputs.length?inputs[index]:null;
                    if(input==null)arguments[index+1]=session.dummy();else{arguments[index+1]=session.input(input);flags|=1<<index;}
                }
                Field destination=session.output(output);arguments[0]=destination.buffer.handle;
                Buffer parameter=session.allocate(Math.max(8,params.length*8L));session.parameters.add(parameter);arguments[4]=parameter.handle;
                if(params.length>0){ok(clEnqueueWriteBuffer(queue,parameter.handle,true,0,params,null,null));UPLOADS.addAndGet(params.length*8L);}
                for(int index=0;index<5;index++)ok(clSetKernelArg1p(k,index,arguments[index]));
                ok(clSetKernelArg1i(k,5,n));ok(clSetKernelArg1i(k,6,size));ok(clSetKernelArg1i(k,7,sea));ok(clSetKernelArg1l(k,8,seed));ok(clSetKernelArg1i(k,11,flags));
                for(int offset=0;offset<output.length;offset+=1024*1024){checkStop(stop);int count=Math.min(1024*1024,output.length-offset);ok(clSetKernelArg1i(k,9,offset));ok(clSetKernelArg1i(k,10,count));ok(clEnqueueNDRangeKernel(queue,k,1,null,stack.pointers(count),null,null,null));}
                destination.dirty=true;
            }
        }
        void residentThermal(Session session,float[] input,float[] output,int n,int iterations,BooleanSupplier stop){
            long first=compile("thermalFlow",THERMAL,"thermalFlow"),second=compile("thermalGather",THERMAL,"thermalGather");Buffer amount=null,flow=null;
            try(MemoryStack stack=MemoryStack.stackPush()){
                long original=session.input(input);Field destination=session.output(output);
                amount=session.allocate(input.length*4L);flow=session.allocate(input.length*4L);
                ok(clEnqueueCopyBuffer(queue,original,destination.buffer.handle,0,0,input.length*4L,null,null));
                for(long k:new long[]{first,second}){ok(clSetKernelArg1p(k,0,destination.buffer.handle));ok(clSetKernelArg1p(k,1,amount.handle));ok(clSetKernelArg1p(k,2,flow.handle));ok(clSetKernelArg1i(k,3,n));}
                for(int pass=0;pass<iterations;pass++){checkStop(stop);ok(clEnqueueNDRangeKernel(queue,first,1,null,stack.pointers(input.length),null,null,null));ok(clEnqueueNDRangeKernel(queue,second,1,null,stack.pointers(input.length),null,null,null));ok(clFinish(queue));}
                destination.dirty=true;
            }finally{if(queue!=0)clFinish(queue);session.release(amount);session.release(flow);}
        }
        void compute(String key,String body,double[] params,float[][] inputs,float[] output,int n,int size,int sea,long seed,BooleanSupplier stop){
            String source=mappingSource(key,body,false);
            long k=compile(key,source,"mapNode");long[] buffers=new long[5];
            try(MemoryStack stack=MemoryStack.stackPush()){
                IntBuffer error=stack.mallocInt(1);int capacity=Math.min(output.length,1024*1024);
                buffers[0]=clCreateBuffer(context,CL_MEM_WRITE_ONLY,capacity*4L,error);ok(error.get(0));
                for(int i=0;i<3;i++){float[] data=i<inputs.length?inputs[i]:null;buffers[i+1]=data==null&&i>0?buffers[1]:upload(data==null?new float[output.length]:data);}
                buffers[4]=clCreateBuffer(context,CL_MEM_READ_ONLY,Math.max(8,params.length*8L),error);ok(error.get(0));
                if(params.length>0){ok(clEnqueueWriteBuffer(queue,buffers[4],true,0,params,null,null));UPLOADS.addAndGet(params.length*8L);}
                for(int i=0;i<5;i++)ok(clSetKernelArg1p(k,i,buffers[i]));ok(clSetKernelArg1i(k,5,n));ok(clSetKernelArg1i(k,6,size));ok(clSetKernelArg1i(k,7,sea));ok(clSetKernelArg1l(k,8,seed));
                FloatBuffer host=MemoryUtil.memAllocFloat(capacity);
                try{for(int offset=0;offset<output.length;offset+=capacity){checkStop(stop);int count=Math.min(capacity,output.length-offset);ok(clSetKernelArg1i(k,9,offset));ok(clSetKernelArg1i(k,10,count));
                    ok(clEnqueueNDRangeKernel(queue,k,1,null,stack.pointers(count),null,null,null));host.clear().limit(count);ok(clEnqueueReadBuffer(queue,buffers[0],true,0,host,null,null));checkStop(stop);host.get(output,offset,count);DOWNLOADS.addAndGet(count*4L);
                }}finally{MemoryUtil.memFree(host);}
            }finally{clFinish(queue);var released=new java.util.HashSet<Long>();for(long buffer:buffers)if(buffer!=0&&released.add(buffer))clReleaseMemObject(buffer);}
        }
        void thermal(float[] height,int n,int iterations,BooleanSupplier stop){
            long first=compile("thermalFlow",THERMAL,"thermalFlow"),second=compile("thermalGather",THERMAL,"thermalGather");long[] buffers=new long[3];
            try(MemoryStack stack=MemoryStack.stackPush()){
                buffers[0]=upload(height);buffers[1]=upload(new float[height.length]);buffers[2]=upload(new float[height.length]);
                for(long k:new long[]{first,second}){for(int i=0;i<3;i++)ok(clSetKernelArg1p(k,i,buffers[i]));ok(clSetKernelArg1i(k,3,n));}
                for(int pass=0;pass<iterations;pass++){checkStop(stop);ok(clEnqueueNDRangeKernel(queue,first,1,null,stack.pointers(height.length),null,null,null));ok(clEnqueueNDRangeKernel(queue,second,1,null,stack.pointers(height.length),null,null,null));ok(clFinish(queue));}
                checkStop(stop);float[] result=new float[height.length];ok(clEnqueueReadBuffer(queue,buffers[0],true,0,result,null,null));checkStop(stop);System.arraycopy(result,0,height,0,height.length);DOWNLOADS.addAndGet(height.length*4L);
            }finally{clFinish(queue);for(long buffer:buffers)if(buffer!=0)clReleaseMemObject(buffer);}
        }
        void close(){if(queue!=0)clFinish(queue);for(long[] item:compiled.values()){clReleaseKernel(item[1]);clReleaseProgram(item[0]);}compiled.clear();if(kernel!=0)clReleaseKernel(kernel);if(program!=0)clReleaseProgram(program);if(queue!=0)clReleaseCommandQueue(queue);if(context!=0)clReleaseContext(context);kernel=program=queue=context=0;}
    }
    private static final String SOURCE="""
        #pragma OPENCL EXTENSION cl_khr_fp64 : enable
        #pragma OPENCL FP_CONTRACT OFF
        double hashNoise(int x,int z,ulong seed){
            ulong v=(ulong)((long)x)*374761393UL+(ulong)((long)z)*668265263UL+seed*1442695040888963407UL;
            v=(v^(v>>13))*1274126177UL;
            return (double)((v^(v>>16))&0x7fffffffUL)/2147483647.0;
        }
        double curve(double x){return x*x*(3.0-2.0*x);}
        double sampleNoise(double x,double z,ulong seed){
            int a=(int)floor(x),b=(int)floor(z);double u=curve(x-a),v=curve(z-b);
            return (hashNoise(a,b,seed)*(1.0-u)+hashNoise(a+1,b,seed)*u)*(1.0-v)
                  +(hashNoise(a,b+1,seed)*(1.0-u)+hashNoise(a+1,b+1,seed)*u)*v;
        }
        kernel void sourceNode(global float* output,int n,int offset,int count,int sea,ulong seed,double scale,int octaves,double persistence,double height,double sharp){
            int j=get_global_id(0);if(j>=count)return;int i=offset+j;
            double x=((i%n)+.5)/n*scale,z=((i/n)+.5)/n*scale;
            double value=0,amp=1,total=0;
            for(int o=0;o<octaves;o++){value+=sampleNoise(x,z,seed+(ulong)(o*71))*amp;total+=amp;amp*=persistence;x*=2.0;z*=2.0;}
            double f=value/total;
            double y=sharp>0?sea+(pow(1.0-fabs(f*2.0-1.0),sharp)-.45)*height*2.0:sea+(f-.4)*height*2.0;
            output[j]=(float)clamp(y,-60.0,1024.0);
        }
        """;
    private static final String THERMAL="""
        #pragma OPENCL FP_CONTRACT OFF
        kernel void thermalFlow(global float* h,global float* amount,global int* flow,int n){
            int i=get_global_id(0),x=i%n,z=i/n;float drop=2;int direction=0;
            if(x>0&&z>0&&x<n-1&&z<n-1){float d=h[i]-h[i-1];if(d>drop){drop=d;direction=1;}
            d=h[i]-h[i+1];if(d>drop){drop=d;direction=2;}d=h[i]-h[i-n];if(d>drop){drop=d;direction=3;}d=h[i]-h[i+n];if(d>drop){drop=d;direction=4;}}
            flow[i]=direction;amount[i]=direction==0?0:(drop-2)*.22f;
        }
        kernel void thermalGather(global float* h,global float* amount,global int* flow,int n){
            int i=get_global_id(0),x=i%n,z=i/n;float delta=0;
            if(z>0&&flow[i-n]==4)delta+=amount[i-n];if(x>0&&flow[i-1]==2)delta+=amount[i-1];
            if(flow[i]!=0)delta-=amount[i];if(x+1<n&&flow[i+1]==1)delta+=amount[i+1];if(z+1<n&&flow[i+n]==3)delta+=amount[i+n];h[i]+=delta;
        }
        """;
}
