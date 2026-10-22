import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Independent GPU snapshot of the current editor map; all OpenGL calls stay on its render thread. */
final class GpuTerrainViewer {
 static final AtomicBoolean RUNNING=new AtomicBoolean();
 static void open(TerraForge.World world){open(world,null);}
 static void open(TerraForge.World world,Path capture){
  if(world==null)return;
  if(!RUNNING.compareAndSet(false,true)){JOptionPane.showMessageDialog(null,"The GPU terrain viewer is already open. Close it to load a fresh snapshot.");return;}
  AtomicReference<TerraForge.World> snapshot=new AtomicReference<>(world);
  Thread thread=new Thread(()->{try{new GpuTerrainViewer().run(snapshot.getAndSet(null),capture,false);}catch(Throwable e){e.printStackTrace();SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,"GPU preview could not start: "+e.getMessage(),"GPU preview",JOptionPane.ERROR_MESSAGE));}finally{RUNNING.set(false);}},"TerraForge-GPU");thread.start();
 }
 long window;int terrainProgram,treeProgram,terrainVao,treeVao,terrainBuffer,treeBuffer,instancesBuffer,heightTexture,colorTexture,treeVertices,treeCount;
 int n,sea,grid=1024;double yaw=-.75,pitch=.72,zoom=1.0,focusX=.5,focusZ=.5,exaggeration=1,lastX,lastY;boolean pointer=false;volatile boolean exporting=false;
 final AtomicReference<Path> exportFile=new AtomicReference<>();
 String renderer;Path autoCapture;boolean exitAfterCapture;
 static final String CAMERA="""
 uniform sampler2D heights;uniform vec2 focus; uniform float yaw,pitch,span,aspect,exaggeration,worldSize,sea;
 vec4 project(vec3 p){vec2 center=texture(heights,focus).rg;float target=max(center.x,center.y);vec3 q=vec3(p.x-focus.x,(p.y-target)*exaggeration/worldSize,p.z-focus.y);
 float co=cos(yaw),si=sin(yaw);float side=q.x*co-q.z*si,forward=q.x*si+q.z*co;
 float up=q.y*cos(pitch)-forward*sin(pitch),depth=-q.y*sin(pitch)-forward*cos(pitch);
 return vec4(side*2.0/(span*aspect),up*2.0/span,depth/2.5,1.0);}
 """;
 static final String TERRAIN_VERTEX="#version 330 core\n"+CAMERA+"""
 uniform int grid;uniform float coverage;uniform vec2 anchor;out vec2 uv;
 void main(){int cell=gl_VertexID/6,k=gl_VertexID%6;int x=cell%grid,z=cell/grid;
 vec2 offset=vec2(0);if(k==1)offset=vec2(1,0);if(k==2||k==4)offset=vec2(1,1);if(k==5)offset=vec2(0,1);
 // Increase sampling density around the focus when zoomed in, maintaining a generous offscreen border.
 uv=anchor+(vec2(x,z)+offset-vec2(float(grid)*.5))/float(grid)*coverage;
 vec2 hw=texture(heights,uv).rg;gl_Position=project(vec3(uv.x,max(hw.x,hw.y),uv.y));}
 """;
 static final String TERRAIN_FRAGMENT="#version 330 core\n"+"""
 uniform sampler2D heights,colors;uniform float worldSize,exaggeration;uniform vec4 hole;in vec2 uv;out vec4 pixel;
 void main(){if(hole.w>0.0&&all(lessThan(abs(uv-hole.xy),vec2(hole.z))))discard;if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1))))discard;
 vec2 hw=texture(heights,uv).rg;float texel=1.0/worldSize;
 float west=texture(heights,uv-vec2(texel,0)).r,east=texture(heights,uv+vec2(texel,0)).r;
 float north=texture(heights,uv-vec2(0,texel)).r,south=texture(heights,uv+vec2(0,texel)).r;
 vec3 normal=normalize(vec3((west-east)*.5,1.0,(north-south)*.5));vec3 light=normalize(vec3(-.5,.85,-.35));
 vec3 color=texture(colors,uv).rgb;bool wet=hw.y>hw.x+.4;
 if(wet){float depth=hw.y-hw.x;vec3 shallow=vec3(.14,.56,.64),deep=vec3(.035,.16,.30);color=mix(shallow,deep,1-exp(-depth/14.0));
 float wave=sin(uv.x*worldSize*.22+sin(uv.y*worldSize*.13))*.006;color+=wave;normal=vec3(0,1,0);}
 float shade=.42+.58*max(dot(normal,light),0);float shadow=1.0;
 if(!wet){for(int i=1;i<=10;i++){float distance=float(i*i)*5.0;vec2 sampleUV=uv+normalize(light.xz)*distance/worldSize;
 if(texture(heights,sampleUV).r>hw.x+distance*1.1+2.0)shadow=min(shadow,.73);}}
 float gloss=wet?pow(max(dot(normalize(vec3(.2,.8,.5)),reflect(-light,normal)),0),48)*.18:0;
 vec3 lit=color*shade*shadow+gloss;pixel=vec4(lit,1);}
 """;
 static final String TREE_VERTEX="#version 330 core\n"+CAMERA+"""
 layout(location=0)in vec3 point;layout(location=1)in vec3 normal;layout(location=2)in vec3 color;layout(location=3)in vec4 tree;
 out vec3 tint;out vec3 faceNormal;
 void main(){vec3 p=vec3(tree.x+point.x*tree.w/worldSize,tree.y+point.y*tree.w,tree.z+point.z*tree.w/worldSize);gl_Position=project(p);tint=color;faceNormal=normal;}
 """;
 static final String TREE_FRAGMENT="""
 #version 330 core
 in vec3 tint;in vec3 faceNormal;out vec4 pixel;
 void main(){float light=.4+.6*max(dot(normalize(faceNormal),normalize(vec3(-.5,.85,-.35))),0);pixel=vec4(tint*light,1);}
 """;
 void run(TerraForge.World world,Path capture,boolean exit)throws Exception{
  RUNNING.set(true);autoCapture=capture;exitAfterCapture=exit;n=world.size;sea=world.sea;exaggeration=n>=4096?4:1;
  GLFWErrorCallback error=GLFWErrorCallback.createPrint(System.err);error.set();
  try{
   if(!glfwInit())throw new IllegalStateException("OpenGL window initialization failed");
   glfwDefaultWindowHints();glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);if(AppHome.mac())glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);/* macOS only offers core contexts that are forward compatible */glfwWindowHint(GLFW_SAMPLES,4);
   window=glfwCreateWindow(1600,1000,"TerraForge GPU · uploading terrain…",0,0);if(window==0)throw new IllegalStateException("OpenGL 3.3 is unavailable");
   glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);
   renderer=glGetString(GL_RENDERER);System.out.println("GPU_RENDERER: "+renderer+" · "+glGetString(GL_VERSION));
   if(n>glGetInteger(GL_MAX_TEXTURE_SIZE))throw new IllegalStateException("Map exceeds the GPU texture-size limit");
   terrainProgram=program(TERRAIN_VERTEX,TERRAIN_FRAGMENT);treeProgram=program(TREE_VERTEX,TREE_FRAGMENT);
   terrainVao=glGenVertexArrays();glBindVertexArray(terrainVao);upload(world);world=null;
   glEnable(GL_DEPTH_TEST);glEnable(GL_MULTISAMPLE);callbacks();title();
   int[] width={1600},height={1000};int frames=0;long start=System.nanoTime();
   while(!glfwWindowShouldClose(window)){
    glfwGetFramebufferSize(window,width,height);if(width[0]>0&&height[0]>0){draw(width[0],height[0]);glfwSwapBuffers(window);frames++;}
    glfwPollEvents();Path output=exportFile.getAndSet(null);if(output!=null)try{capture(output);}catch(Exception e){e.printStackTrace();SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,"Could not save the 4K render: "+e.getMessage()));}
    if(autoCapture!=null&&frames>=3){capture(autoCapture);autoCapture=null;if(exitAfterCapture)break;}
    if(frames==120)System.out.println("GPU_PREVIEW_READY: "+renderer+" · full "+n+"² height texture · "+(grid*grid*2)+" terrain triangles · "+treeCount+" tree previews · first 120 frames "+String.format(java.util.Locale.ROOT,"%.1f",(System.nanoTime()-start)/1e9)+"s");
    Thread.sleep(8);
   }
  }finally{
   if(window!=0){glDeleteProgram(terrainProgram);glDeleteProgram(treeProgram);glDeleteTextures(heightTexture);glDeleteTextures(colorTexture);glDeleteVertexArrays(terrainVao);glDeleteVertexArrays(treeVao);glDeleteBuffers(terrainBuffer);glDeleteBuffers(treeBuffer);glDeleteBuffers(instancesBuffer);org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window);}GL.setCapabilities(null);glfwTerminate();error.free();
  }
 }
 void upload(TerraForge.World w){
  heightTexture=glGenTextures();glBindTexture(GL_TEXTURE_2D,heightTexture);textureParameters();glTexImage2D(GL_TEXTURE_2D,0,GL_RG32F,n,n,0,GL_RG,GL_FLOAT,(ByteBuffer)null);
  int cell=w.cellBlocks();int rows=Math.min(64,n);FloatBuffer strip=MemoryUtil.memAllocFloat(n*rows*2);
  try{for(int z=0;z<n;z+=rows){int count=Math.min(rows,n-z);strip.clear();for(int zz=0;zz<count;zz++)for(int x=0;x<n;x++){int i=(z+zz)*n+x;strip.put(w.height[i]/cell);strip.put(w.environment.waterY(i,sea)/(float)cell);}strip.flip();glTexSubImage2D(GL_TEXTURE_2D,0,0,z,n,count,GL_RG,GL_FLOAT,strip);glfwPollEvents();}}finally{MemoryUtil.memFree(strip);}
  int side=Math.min(n,4096);colorTexture=glGenTextures();glBindTexture(GL_TEXTURE_2D,colorTexture);textureParameters();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,side,side,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
  ByteBuffer colors=MemoryUtil.memAlloc(side*4*32);
  try{for(int z=0;z<side;z+=32){colors.clear();int count=Math.min(32,side-z);for(int zz=0;zz<count;zz++)for(int x=0;x<side;x++){int sample=EnvironmentView.riverSample(w,x,z+zz,side),xx=sample%n,rz=sample/n;java.awt.Color c=w.waterY(xx,rz)>w.h(xx,rz)?new java.awt.Color(48,112,165):EnvironmentView.surfaceColor(w,xx,rz);colors.put((byte)c.getRed()).put((byte)c.getGreen()).put((byte)c.getBlue()).put((byte)255);}colors.flip();glTexSubImage2D(GL_TEXTURE_2D,0,0,z,side,count,GL_RGBA,GL_UNSIGNED_BYTE,colors);glfwPollEvents();}}finally{MemoryUtil.memFree(colors);}
  uploadTrees(w);int error=glGetError();if(error!=GL_NO_ERROR)throw new IllegalStateException("GPU terrain upload failed (OpenGL "+error+")");
 }
 static void textureParameters(){glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}
 static int program(String vertex,String fragment){int v=shader(GL_VERTEX_SHADER,vertex),f=shader(GL_FRAGMENT_SHADER,fragment),p=glCreateProgram();try{glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);if(glGetProgrami(p,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(p));return p;}finally{glDeleteShader(v);glDeleteShader(f);}}
 static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);if(glGetShaderi(s,GL_COMPILE_STATUS)==GL_FALSE){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
 void uniform(int p,String name,float value){glUniform1f(glGetUniformLocation(p,name),value);}
 void camera(int p,int width,int height){uniform(p,"yaw",(float)yaw);uniform(p,"pitch",(float)pitch);uniform(p,"span",(float)(Math.max(1.05,1.48*height/width)/zoom));uniform(p,"aspect",width/(float)height);uniform(p,"worldSize",n);uniform(p,"sea",sea);uniform(p,"exaggeration",(float)exaggeration);glUniform2f(glGetUniformLocation(p,"focus"),(float)focusX,(float)focusZ);}
 void draw(int width,int height){if(!RUNNING.get())glfwSetWindowShouldClose(window,true);glViewport(0,0,width,height);glClearColor(.43f,.64f,.79f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(terrainProgram);camera(terrainProgram,width,height);glUniform1i(glGetUniformLocation(terrainProgram,"grid"),grid);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,heightTexture);glUniform1i(glGetUniformLocation(terrainProgram,"heights"),0);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,colorTexture);glUniform1i(glGetUniformLocation(terrainProgram,"colors"),1);glBindVertexArray(terrainVao);float coverage=(float)Math.min(1,1.05/zoom*3.2);
  int hole=glGetUniformLocation(terrainProgram,"hole"),anchor=glGetUniformLocation(terrainProgram,"anchor");
  if(coverage<1){glUniform1i(glGetUniformLocation(terrainProgram,"grid"),512);uniform(terrainProgram,"coverage",1);glUniform2f(anchor,.5f,.5f);glUniform4f(hole,(float)focusX,(float)focusZ,coverage*.5f,1);glDrawArrays(GL_TRIANGLES,0,512*512*6);}
  glUniform4f(hole,0,0,0,0);glUniform1i(glGetUniformLocation(terrainProgram,"grid"),grid);uniform(terrainProgram,"coverage",coverage);glUniform2f(anchor,coverage<1?(float)focusX:.5f,coverage<1?(float)focusZ:.5f);glDrawArrays(GL_TRIANGLES,0,grid*grid*6);
  if(treeCount>0){glUseProgram(treeProgram);camera(treeProgram,width,height);glBindVertexArray(treeVao);glDrawArraysInstanced(GL_TRIANGLES,0,treeVertices,treeCount);}glUseProgram(0);
 }
 void title(){glfwSetWindowTitle(window,"TerraForge GPU · "+renderer+" · Orbit: left drag | Pan: right drag | Zoom: wheel | H: "+(int)exaggeration+"× height | F: fit | P: save 4096² | Esc: close");}
 void callbacks(){
  glfwSetScrollCallback(window,(win,x,y)->{zoom=Math.max(.5,Math.min(64,zoom*Math.pow(1.15,y)));});
  glfwSetCursorPosCallback(window,(win,x,y)->{if(pointer){double dx=x-lastX,dy=y-lastY;if(glfwGetMouseButton(win,GLFW_MOUSE_BUTTON_LEFT)==GLFW_PRESS){yaw+=dx*.006;pitch=Math.max(.15,Math.min(1.5,pitch+dy*.005));}if(glfwGetMouseButton(win,GLFW_MOUSE_BUTTON_RIGHT)==GLFW_PRESS){double s=.001/zoom,co=Math.cos(yaw),si=Math.sin(yaw);focusX=Math.max(0,Math.min(1,focusX-dx*s*co-dy*s*si));focusZ=Math.max(0,Math.min(1,focusZ+dx*s*si-dy*s*co));}}lastX=x;lastY=y;pointer=true;});
  glfwSetKeyCallback(window,(win,key,scan,action,mods)->{if(action!=GLFW_PRESS)return;if(key==GLFW_KEY_ESCAPE)glfwSetWindowShouldClose(win,true);if(key==GLFW_KEY_F){zoom=1;focusX=focusZ=.5;yaw=-.75;pitch=.72;}if(key==GLFW_KEY_H){exaggeration=exaggeration==1?4:1;title();}if(key==GLFW_KEY_P)chooseExport();if(key==GLFW_KEY_F11){long monitor=glfwGetWindowMonitor(win);if(monitor==0){long primary=glfwGetPrimaryMonitor();var mode=glfwGetVideoMode(primary);glfwSetWindowMonitor(win,primary,0,0,mode.width(),mode.height(),mode.refreshRate());}else glfwSetWindowMonitor(win,0,60,60,1600,1000,0);}});
 }
 void chooseExport(){if(exporting)return;SwingUtilities.invokeLater(()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new java.io.File("TerraForge-4096.png"));if(c.showSaveDialog(null)==JFileChooser.APPROVE_OPTION){Path p=c.getSelectedFile().toPath();if(!p.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))p=Path.of(p+".png");if(Files.exists(p)&&JOptionPane.showConfirmDialog(null,"Replace "+p.getFileName()+"?","Save 4K render",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;exportFile.set(p);}});}
 void capture(Path file)throws Exception{
  exporting=true;int width=4096,height=4096,ms=glGenFramebuffers(),resolve=glGenFramebuffers(),color=glGenRenderbuffers(),depth=glGenRenderbuffers(),texture=glGenTextures();
  try{glBindFramebuffer(GL_FRAMEBUFFER,ms);glBindRenderbuffer(GL_RENDERBUFFER,color);glRenderbufferStorageMultisample(GL_RENDERBUFFER,4,GL_RGBA8,width,height);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,color);glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorageMultisample(GL_RENDERBUFFER,4,GL_DEPTH_COMPONENT24,width,height);glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("4K framebuffer unavailable");int oldGrid=grid;try{grid=2048;draw(width,height);}finally{grid=oldGrid;}
   glBindFramebuffer(GL_FRAMEBUFFER,resolve);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("4K resolve framebuffer unavailable");glBindFramebuffer(GL_READ_FRAMEBUFFER,ms);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,resolve);glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);glBindFramebuffer(GL_FRAMEBUFFER,resolve);
   ByteBuffer data=MemoryUtil.memAlloc(width*height*4);try{glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);int[] pixels=((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=((height-1-y)*width+x)*4;pixels[y*width+x]=(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|(data.get(i+2)&255);}Path target=file.toAbsolutePath();Files.createDirectories(target.getParent());if(!ImageIO.write(image,"png",target.toFile()))throw new java.io.IOException("PNG writer unavailable");System.out.println("GPU_4K_SAVED: "+target+" · 4096 × 4096 · 4× MSAA · "+renderer);}finally{MemoryUtil.memFree(data);}
  }finally{glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(ms);glDeleteFramebuffers(resolve);glDeleteRenderbuffers(color);glDeleteRenderbuffers(depth);glDeleteTextures(texture);exporting=false;}
 }
 void uploadTrees(TerraForge.World w){
  ArrayList<Float> vertices=new ArrayList<>();for(int layer=0;layer<3;layer++){float base=layer==0?0:layer==1?.2f:.5f,top=layer==0?.55f:layer==1?.82f:1f,r=layer==0?.045f:layer==1?.24f:.18f;for(int j=0;j<8;j++){double a=j*Math.PI/4,b=(j+1)*Math.PI/4;float[] p={(float)Math.cos(a)*r,base,(float)Math.sin(a)*r,(float)Math.cos(b)*r,base,(float)Math.sin(b)*r,0,top,0};float[] normal={(float)Math.cos((a+b)*.5),.35f,(float)Math.sin((a+b)*.5)};float[] color=layer==0?new float[]{.30f,.21f,.12f}:new float[]{.12f,.29f,.15f};for(int k=0;k<3;k++){for(int v=0;v<3;v++)vertices.add(p[k*3+v]);for(float v:normal)vertices.add(v);for(float v:color)vertices.add(v);}}}
  treeVertices=vertices.size()/9;FloatBuffer mesh=MemoryUtil.memAllocFloat(vertices.size());for(float v:vertices)mesh.put(v);mesh.flip();treeVao=glGenVertexArrays();glBindVertexArray(treeVao);treeBuffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,treeBuffer);glBufferData(GL_ARRAY_BUFFER,mesh,GL_STATIC_DRAW);MemoryUtil.memFree(mesh);for(int i=0;i<3;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,3,GL_FLOAT,false,36,i*12L);}
  var trees=w.environment.trees;treeCount=trees.size();if(treeCount==0)return;FloatBuffer instances=MemoryUtil.memAllocFloat(treeCount*4);try{for(var p:trees){var tree=TreeLibrary.catalog().trees().get(p.tree());instances.put(p.x()/(float)n).put(w.height[p.z()*n+p.x()]).put(p.z()/(float)n).put(tree.height);}instances.flip();instancesBuffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,instancesBuffer);glBufferData(GL_ARRAY_BUFFER,instances,GL_STATIC_DRAW);glEnableVertexAttribArray(3);glVertexAttribPointer(3,4,GL_FLOAT,false,16,0);glVertexAttribDivisor(3,1);}finally{MemoryUtil.memFree(instances);}
 }
 public static void main(String[]args)throws Exception{
  TerraForge.World w;if(args[0].equals("--test")){w=TerraForge.generateWorld(512,62,2024,170,"Island");}else{w=TerraForge.readProject(Path.of(args[0]));}
  new GpuTerrainViewer().run(w,args.length>1?Path.of(args[1]):null,args.length>2&&args[2].equals("--exit"));
 }
}
