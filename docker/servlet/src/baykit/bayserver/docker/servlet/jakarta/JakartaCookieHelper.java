package baykit.bayserver.docker.servlet.jakarta;

import jakarta.servlet.http.Cookie;
import baykit.bayserver.docker.servlet.duck.CookieHelper;

import java.util.ArrayList;

public class JakartaCookieHelper implements CookieHelper {
    @Override
    public Object newCookie(String name, String value) {
        return new Cookie(name, value);
    }

    @Override
    public Object[] newArray(int size) {
        return new Cookie[size];
    }

    @Override
    public String getName(Object cookie) {
        return ((Cookie)cookie).getName();
    }

    @Override
    public String getValue(Object cookie) {
        return ((Cookie)cookie).getValue();
    }

    @Override
    public int getMaxAge(Object cookie) {
        return ((Cookie)cookie).getMaxAge();
    }

    @Override
    public int getVersion(Object cookie) {
        return ((Cookie)cookie).getVersion();
    }

    @Override
    public String getPath(Object cookie) {
        return ((Cookie)cookie).getPath();
    }

    @Override
    public String getDomain(Object cookie) {
        return ((Cookie)cookie).getDomain();
    }

    @Override
    public boolean getSecure(Object cookie) {
        return ((Cookie)cookie).getSecure();
    }

    @Override
    public Object[] toArray(ArrayList<Object> cookies) {
        return cookies.toArray(new Cookie[0]);
    }

    @Override
    public void setPath(Object cookie, String path) {
        ((Cookie)cookie).setPath(path);
    }
}
