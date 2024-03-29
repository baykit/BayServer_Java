package yokohama.baykit.bayserver.docker.servlet.javax;

import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.servlet.ServletDocker;
import yokohama.baykit.bayserver.docker.servlet.duck.HttpServletResponseDuck;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class JavaxHttpServletResponse extends HttpServletResponseDuck implements HttpServletResponse {

    public JavaxHttpServletResponse(Tour tour, ServletDocker docker) {
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