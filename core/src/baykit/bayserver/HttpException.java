package baykit.bayserver;

import baykit.bayserver.util.HttpStatus;

public class HttpException extends BayException {

    // HTTP Status
    public final int status;
    public String location; // for 302

    public HttpException(int status, Throwable exception, String fmt, String ...args) {
        super(exception, fmt, (Object[])args);
        if(status < 300 || status >= 600)
            throw new IllegalArgumentException(Integer.toString(status));

        // Line an assertion
        if (exception instanceof HttpException)
            throw new IllegalStateException();

        this.status = status;
    }

    public HttpException(int status, Throwable exception) {
        this(status, exception, null);
    }

    public HttpException(int status, String message) {
        this(status, null, message);
    }

    public HttpException(int status) {
        this(status, null, null);
    }

    public String getMessage() {
        return "HTTP " + status + " " + HttpStatus.description(status) + ": "
                + (super.getMessage() == null ? "" : super.getMessage());
    }

    public int getStatus() {
        return status;
    }


    public static HttpException movedTemp(String location) {
        HttpException e = new HttpException(HttpStatus.MOVED_TEMPORARILY, location);
        e.location = location;
        return e;
    }
}