package yokohama.baykit.bayserver.boot;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

public class Boot {

    final static String BOOTSTRAP_JAR = "bootstrap.jar";

    private static final String BSERV_MAIN_CLASS = "yokohama.baykit.bayserver.BayServer";
    private static final String OPT_BOOTLOG = "-bootlog";
    private static final String OPT_HOME = "-home";
    private static final String ENV_HOME = "BSERV_HOME";

    public static boolean debugMode;
       
    public static void main(String[] args) throws Exception{

        String bservHome = System.getenv(ENV_HOME);
        boolean addHomeOpt = (bservHome == null);

        for (String arg : args) {
            if (arg.toLowerCase().startsWith(OPT_BOOTLOG))
                debugMode = true;
            if (arg.toLowerCase().startsWith(OPT_HOME)) {
                bservHome = arg.substring(6);
                addHomeOpt = false;
            }
        }

        if(bservHome == null) {
            // Get BayServer home
            bservHome = ".";
            bservHome = new File(bservHome).getAbsolutePath();
            bservHome = bservHome.replace(File.separatorChar, '/');
            if (bservHome.endsWith("/"))
                bservHome = bservHome.substring(0, bservHome.length() - 1);
        }

        debug("Bayserver home: " + bservHome);

        // The directory which BayServer core libs exist
        File libDir = new File(bservHome, "lib");

        // URL list
        ArrayList<URL> urls = new ArrayList<>();

        // Add class path
        File[] files = libDir.listFiles();
        if(files != null) {
            for(File lib: files) {
                if(lib.getName().endsWith(".jar") ||
                    lib.getName().endsWith(".zip")) {
                    debug("Add library: " + lib);
                    urls.add(lib.toURI().toURL());
                }
            }
        }

        // Invoke BayServer
        ClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), Boot.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        
        Class cls = loader.loadClass(BSERV_MAIN_CLASS);
        Method m = cls.getMethod("main",
                                 new Class[] { String[].class });

        if(addHomeOpt) {
            String[] newArgs = new String[args.length + 1];
            System.arraycopy(args, 0, newArgs, 0, args.length);
            newArgs[args.length] = "-home=" + bservHome;
            args = newArgs;
        }
        debug("Invoke BayServer");
        if(debugMode) {
            for(int i = 0; i < args.length; i++) {
                debug("args[" + i + "]=" + args[i]);
            }
        }
        try {
            m.invoke(null, new Object[] {args});
        }
        catch(InvocationTargetException e) {
            e.getTargetException().printStackTrace();
        }
    }


    /**
     * Guess Bayserver home from classpath
     * @return BayServer home
     */
    static String guessBayServerHome() {
        String classPath = System.getProperty("java.class.path");
        StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
        String jarPath = null;
        while(st.hasMoreTokens()) {
            String path = st.nextToken();
            debug("class path element: " + path);
            if(path.endsWith(BOOTSTRAP_JAR)) {
                jarPath = path;
            }
        }
        debug("bootstrap.jar path: " + jarPath);

        if(jarPath == null) {
            System.err.println(BOOTSTRAP_JAR + " not found in classpath: " + classPath);
            System.exit(1);
        }

        File homedir;
        File jarFile = new File(jarPath);
        File bindir = jarFile.getParentFile();
        debug("bindir: " + bindir);
        if (bindir != null) {
            homedir = bindir.getParentFile();
            if(homedir == null) {
                homedir = new File(".");
            }
        }
        else
            homedir = new File("..");
        debug("homedir: " + homedir);


        if(!homedir.exists()) {
            System.err.println("BayServer Home does not exist: " + homedir.getPath());
            System.exit(1);
        }
        
        return homedir.getPath();
    }


    /**
     * Print debug message
     * @param msg
     */
    static void debug(String msg) {
        if(debugMode)
            System.out.println("[boot] " + msg);
    }
}
