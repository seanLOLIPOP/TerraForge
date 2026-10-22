import java.nio.file.*;
import java.util.Properties;
import static org.lwjgl.glfw.GLFW.*;
/**
 * Shared mouse and keyboard tuning for the 3D views (GPU 3D, Minecraft view, Whole map blocks and the editor's 3D).
 * Sensitivity and fly speed are saved in controls.properties beside the app, so they carry over between sessions.
 *   [ and ]      lower / raise mouse sensitivity (all views)
 *   mouse wheel  fly speed in the Minecraft and Whole map views
 * On Linux the cursor is re-centred while the mouse is captured, which inflates and jitters cursor deltas;
 * raw mouse motion avoids that, and single oversized jumps are ignored.
 */
final class ViewControls {
    static final double MIN=.1,MAX=3;
    private static double sensitivity=Double.NaN,flySpeed=Double.NaN;

    private static Path file(){return AppHome.data("controls.properties");}
    private static synchronized void load(){
        if(!Double.isNaN(sensitivity))return;
        sensitivity=.5;flySpeed=1;
        try{var p=new Properties();try(var in=Files.newInputStream(file())){p.load(in);}
            sensitivity=clamp(Double.parseDouble(p.getProperty("mouseSensitivity","0.5")),MIN,MAX);
            flySpeed=clamp(Double.parseDouble(p.getProperty("flySpeed","1")),.1,20);}
        catch(Exception ignored){}
    }
    private static synchronized void save(){
        try{var p=new Properties();p.setProperty("mouseSensitivity",Double.toString(sensitivity));p.setProperty("flySpeed",Double.toString(flySpeed));
            Files.createDirectories(file().getParent());try(var out=Files.newOutputStream(file())){p.store(out,"TerraForge 3D view controls");}}
        catch(Exception ignored){}
    }
    static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    /** Mouse multiplier: 1.0 is the old (fast) speed, the default is 0.5. */
    static double sensitivity(){load();return sensitivity;}
    static double flySpeed(){load();return flySpeed;}
    /** Each press changes the mouse speed by 10% of the default. */
    static void adjustSensitivity(boolean up){load();sensitivity=clamp(Math.round((sensitivity+(up?.05:-.05))*100)/100.0,MIN,MAX);save();}
    static void adjustFlySpeed(double wheel){load();flySpeed=clamp(flySpeed*Math.pow(1.2,wheelStep(wheel)),.1,20);save();}
    static String label(){return "Mouse "+Math.round(sensitivity()*200)+"% ([ ])";}
    static String speedLabel(){return String.format(java.util.Locale.ROOT,"Speed %.1f× (wheel)",flySpeed());}
    /** A single cursor jump this large is a re-centre or focus glitch, not a real movement. */
    static double delta(double d){return Math.abs(d)>200?0:d;}
    /** Smooth-scrolling wheels and touchpads send large or many small steps; limit each event to one notch. */
    static double wheelStep(double y){return clamp(y,-1,1);}
    /** Turn on raw (unaccelerated, un-warped) mouse motion while the cursor is captured, where the system supports it. */
    static void captureMouse(long window){
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_DISABLED);
        try{if(glfwRawMouseMotionSupported())glfwSetInputMode(window,GLFW_RAW_MOUSE_MOTION,GLFW_TRUE);}catch(Throwable ignored){}
    }
    static void releaseMouse(long window){
        try{if(glfwRawMouseMotionSupported())glfwSetInputMode(window,GLFW_RAW_MOUSE_MOTION,GLFW_FALSE);}catch(Throwable ignored){}
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_NORMAL);
    }
}
