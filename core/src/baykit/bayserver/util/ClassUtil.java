package baykit.bayserver.util;

public class ClassUtil {
    public static String getLocalName(Class cls) {
        String name = cls.getName();
        int p = name.lastIndexOf('.');
        if(p > 0)
            name = name.substring(p + 1);
        return name;
    }
}
