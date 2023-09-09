package yokohama.baykit.bayserver;

import yokohama.baykit.bayserver.util.HttpStatus;

public class HttpException extends BayException {

    // HTTP Status
    public final int status;
    public String location; // for 302

    public HttpException(int status, String fmt, Object ...args) {
        super(fmt, (Object[])args);
        if(status < 300 || status >= 600)
            throw new IllegalArgumentException(Integer.toString(status));

        this.status = status;
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