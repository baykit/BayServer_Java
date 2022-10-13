package baykit.bayserver.docker.servlet.jakarta;

import baykit.bayserver.tour.Tour;
import baykit.bayserver.docker.servlet.ServletDocker;
import baykit.bayserver.docker.servlet.duck.HttpServletResponseDuck;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class JakartaHttpServletResponse extends HttpServletResponseDuck implements HttpServletResponse {

    public JakartaHttpServletResponse(Tour tour, ServletDocker docker) {
        super(tour, docker);
    }

    //////////////////////////////////////////////////////////////////////
    // Override methods
    //////////////////////////////////////////////////////////////////////
    @Override
    public void addCookie(Cookie cookie) { 
        addCookieObject(cookie);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return (ServletOutputStream)getOutputStreamObject();
    }
}