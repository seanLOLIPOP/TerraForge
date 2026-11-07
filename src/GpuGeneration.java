import java.nio.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.*;
import org.lwjgl.system.*;
import static org.lwjgl.opencl.CL10.*;

/** Optional vendor-neutral OpenCL source nodes. One bounded queue, independent of viewer contexts. */
final class GpuGeneration {
    private static Backend backend;
    private static boolean attempted;
    private static final java.util.concurrent.atomic.AtomicLong JOBS=new java.util.concurrent.atomic.AtomicLong();
    private static volatile String status="GPU not initialized";
    static String status(){return status;}
    static long jobs(){return JOBS.get();}
    static boolean enabled(){return !System.getProperty("terraforge.gpu","auto").equalsIgnoreCase("cpu");}
    private static Backend backend(){if(!attempted){attempted=true;backend=new Backend();status="OpenCL · "+backend.name;}return backend;}
    private static void failed(Throwable error){if(backend!=null){backend.close();backend=null;}String message=String.valueOf(error.getMessage());String brief=message.lines().findFirst().orElse(error.getClass().getSimpleName());status="CPU fallback · "+brief.substring(0,Math.min(240,brief.length()));System.err.println("Terrain generation: CPU fallback · "+message);}
    /** All calls are serialized; region workers never create competing GPU contexts. */
    static synchronized boolean compute(String key,String body,double[] params,float[][] inputs,float[] output,int n,int size,int sea,long seed,BooleanSupplier stop){
        long bytes=output.length*8L;for(float[] input:inputs)if(input!=null)bytes+=input.length*4L;
        if(!enabled()||n<512||bytes>(256L<<20))return false;
        checkStop(stop);
        try{Backend b=backend();if(b==null||bytes>b.budget)return false;b.compute(key,body,params,inputs,output,n,size,sea,seed,stop);JOBS.incrementAndGet();return true;}
        catch(CancellationException cancelled){throw cancelled;}
        catch(LinkageError|RuntimeException error){failed(error);return false;}
    }
    static synchronized boolean thermal(float[] height,int n,int iterations,BooleanSupplier stop){
        if(!enabled()||n<512||iterations<2||height.length*13L>(256L<<20))return false;
        checkStop(stop);
        try{Backend b=backend();if(b==null||height.length*13L>b.budget)return false;b.thermal(height,n,iterations,stop);JOBS.incrementAndGet();return true;}
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
            backend.generate(node,n,sea,seed,output,stop);JOBS.incrementAndGet();return true;
        }catch(CancellationException cancelled){throw cancelled;}
        catch(LinkageError|RuntimeException error){
            failed(error);return false;
        }
    }
    private static void ok(int code){if(code!=CL_SUCCESS)throw new IllegalStateException("OpenCL error "+code);}
    private static final class Backend {
        long context,queue,program,kernel,device,budget;String name;
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
                budget=Math.min(256L<<20,bestMemory/4);
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
                    host.clear().limit(count);ok(clEnqueueReadBuffer(queue,buffer,true,0,host,null,null));checkStop(stop);host.get(output,offset,count);
                }
            }finally{if(buffer!=0){clFinish(queue);clReleaseMemObject(buffer);}MemoryUtil.memFree(host);}
        }
        long compile(String key,String source,String function){
            long[] existing=compiled.get(key);if(existing!=null)return existing[1];
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
            try{if(data!=null)ok(clEnqueueWriteBuffer(queue,buffer,true,0,data,null,null));return buffer;}catch(RuntimeException e){clReleaseMemObject(buffer);throw e;}
        }}
        void compute(String key,String body,double[] params,float[][] inputs,float[] output,int n,int size,int sea,long seed,BooleanSupplier stop){
            String source=SOURCE+"\n double fclamp(double v,double lo,double hi){return (float)clamp(v,lo,hi); }\n"+"\n double fractal(double x,double z,ulong seed,int octaves,double persistence){double value=0,amp=1,total=0;for(int o=0;o<octaves;o++){value+=sampleNoise(x,z,seed+(ulong)(o*71))*amp;total+=amp;amp*=persistence;x*=2.0;z*=2.0;}return value/total;}\n"+
                (key.startsWith("MOUNTAIN:")?GpuMountains.HELPERS:"")+(key.startsWith("ORGANIC_MATERIALS:")?GpuMaterials.helpers()+GpuZones.HELPERS:"")+"kernel void mapNode(global float* out,global const float* A,global const float* B,global const float* C,global const double* params,int n,int size,int sea,ulong seed,int offset,int batchCount){int j=get_global_id(0);if(j>=batchCount)return;int oi=j+offset,channel=oi/(n*n),i=oi%(n*n),x=i%n,z=i/n;double a=A[i],b=B[i],c=C[i],value=a;"+body+"out[j]=(float)value;}";
            long k=compile(key,source,"mapNode");long[] buffers=new long[5];
            try(MemoryStack stack=MemoryStack.stackPush()){
                IntBuffer error=stack.mallocInt(1);int capacity=Math.min(output.length,1024*1024);
                buffers[0]=clCreateBuffer(context,CL_MEM_WRITE_ONLY,capacity*4L,error);ok(error.get(0));
                for(int i=0;i<3;i++){float[] data=i<inputs.length?inputs[i]:null;buffers[i+1]=data==null&&i>0?buffers[1]:upload(data==null?new float[output.length]:data);}
                buffers[4]=clCreateBuffer(context,CL_MEM_READ_ONLY,Math.max(8,params.length*8L),error);ok(error.get(0));
                if(params.length>0)ok(clEnqueueWriteBuffer(queue,buffers[4],true,0,params,null,null));
                for(int i=0;i<5;i++)ok(clSetKernelArg1p(k,i,buffers[i]));ok(clSetKernelArg1i(k,5,n));ok(clSetKernelArg1i(k,6,size));ok(clSetKernelArg1i(k,7,sea));ok(clSetKernelArg1l(k,8,seed));
                FloatBuffer host=MemoryUtil.memAllocFloat(capacity);
                try{for(int offset=0;offset<output.length;offset+=capacity){checkStop(stop);int count=Math.min(capacity,output.length-offset);ok(clSetKernelArg1i(k,9,offset));ok(clSetKernelArg1i(k,10,count));
                    ok(clEnqueueNDRangeKernel(queue,k,1,null,stack.pointers(count),null,null,null));host.clear().limit(count);ok(clEnqueueReadBuffer(queue,buffers[0],true,0,host,null,null));checkStop(stop);host.get(output,offset,count);
                }}finally{MemoryUtil.memFree(host);}
            }finally{clFinish(queue);var released=new java.util.HashSet<Long>();for(long buffer:buffers)if(buffer!=0&&released.add(buffer))clReleaseMemObject(buffer);}
        }
        void thermal(float[] height,int n,int iterations,BooleanSupplier stop){
            long first=compile("thermalFlow",THERMAL,"thermalFlow"),second=compile("thermalGather",THERMAL,"thermalGather");long[] buffers=new long[3];
            try(MemoryStack stack=MemoryStack.stackPush()){
                buffers[0]=upload(height);buffers[1]=upload(new float[height.length]);buffers[2]=upload(new float[height.length]);
                for(long k:new long[]{first,second}){for(int i=0;i<3;i++)ok(clSetKernelArg1p(k,i,buffers[i]));ok(clSetKernelArg1i(k,3,n));}
                for(int pass=0;pass<iterations;pass++){checkStop(stop);ok(clEnqueueNDRangeKernel(queue,first,1,null,stack.pointers(height.length),null,null,null));ok(clEnqueueNDRangeKernel(queue,second,1,null,stack.pointers(height.length),null,null,null));ok(clFinish(queue));}
                checkStop(stop);float[] result=new float[height.length];ok(clEnqueueReadBuffer(queue,buffers[0],true,0,result,null,null));checkStop(stop);System.arraycopy(result,0,height,0,height.length);
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
