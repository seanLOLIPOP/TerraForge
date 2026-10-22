import java.nio.file.*;
/**
 * Portable storage: TerraForge keeps its working files (region builds, recovery) inside its own folder,
 * next to TerraForge.jar, so the whole app can be moved or run from a USB drive. If that folder is not
 * writable, it falls back to ~/.terraforge.
 */
final class AppHome {
    private static volatile Path dir;
    static Path dir(){
        if(dir!=null)return dir;
        Path found=null;
        String forced=System.getProperty("terraforge.home");
        if(forced!=null&&!forced.isBlank())found=Path.of(forced);
        else try{Path code=Path.of(TerraForge.class.getProtectionDomain().getCodeSource().getLocation().toURI());found=Files.isRegularFile(code)?code.getParent():code.getParent();}catch(Exception e){found=null;}
        if(found==null||!writable(found))found=Path.of(System.getProperty("user.home"),".terraforge");
        dir=found.toAbsolutePath().normalize();
        return dir;
    }
    static boolean writable(Path p){
        try{Files.createDirectories(p);Path probe=Files.createTempFile(p,".write-test",".tmp");Files.delete(probe);return true;}catch(Exception e){return false;}
    }
    /** A file or folder inside the app's data area. */
    static Path data(String name){return dir().resolve(name);}
    static boolean mac(){return System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("mac");}
    static boolean windows(){return System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("win");}
    /** The Minecraft Java game folder for this operating system. */
    static Path minecraftDir(){
        Path home=Path.of(System.getProperty("user.home"));
        if(mac())return home.resolve("Library/Application Support/minecraft");
        if(windows()){String appdata=System.getenv("APPDATA");return (appdata==null?home.resolve("AppData/Roaming"):Path.of(appdata)).resolve(".minecraft");}
        return home.resolve(".minecraft");
    }
}
