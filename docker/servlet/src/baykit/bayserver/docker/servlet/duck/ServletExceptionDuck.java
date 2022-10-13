package baykit.bayserver.docker.servlet.duck;

public class ServletExceptionDuck extends Exception {
    public ServletExceptionDuck(Exception servletException) {
        super(servletException);
    }

    public Exception getServletException() {
        return (Exception) getCause();
    }

}
