package baykit.bayserver.docker.servlet.duck;

import java.util.ArrayList;

public interface CookieHelper {
    Object newCookie(String name, String value);

    Object[] newArray(int size);

    String getName(Object cookie);

    String getValue(Object cookie);

    int getMaxAge(Object cookie);

    int getVersion(Object cookie);

    String getPath(Object cookie);

    String getDomain(Object cookie);

    boolean getSecure(Object cookie);

    Object[] toArray(ArrayList<Object> cookies);

    void setPath(Object cookie, String contextPath);
}
