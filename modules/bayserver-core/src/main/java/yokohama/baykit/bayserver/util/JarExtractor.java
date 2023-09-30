package yokohama.baykit.bayserver.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarExtractor {


    public void extract(String jarPath, String dest) throws IOException {
        File destDir = new File(dest);

        if (!destDir.exists() && !destDir.mkdirs())
                throw new IOException("Cannot create directory: " + destDir);

        try (JarFile jarFile = new JarFile(jarPath)) {

            for(Enumeration<JarEntry> en = jarFile.entries(); en.hasMoreElements();) {
                JarEntry ent = en.nextElement();
                String name = ent.getName();
                File outFile = new File(dest + File.separator + name);

                if(name.startsWith("META-INF")) {
                    // ignore
                }
                else if (ent.isDirectory()) {
                    if(!outFile.exists() && !outFile.mkdirs())
                        throw new IOException("Cannot create directory: " + outFile);
                }
                else {
                    try (InputStream in = jarFile.getInputStream(ent);
                         FileOutputStream out = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[1024];
                        int c;
                        while ((c = in.read(buf)) != -1) {
                            out.write(buf, 0, c);
                        }
                    }
                }
            }
        }
    }
}
