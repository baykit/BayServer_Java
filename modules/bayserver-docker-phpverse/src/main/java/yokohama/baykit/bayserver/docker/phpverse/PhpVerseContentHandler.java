package yokohama.baykit.bayserver.docker.phpverse;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Per-tour content handler: when the request body is fully received,
 * runs the requested .php file via the embedded libphp runtime.
 *
 * <p>The runtime streams PHP's {@code echo} output directly to
 * {@code tour.res.sendResContent} from inside its ub_write upcall, so
 * the only thing this handler does after dispatching the script is
 * close the response (or, if PHP wrote nothing, send a zero-byte 200).
 */
public class PhpVerseContentHandler implements ReqContentHandler {

    private final Tour tour;
    private final Path file;
    private final PhpVerseRuntime runtime;

    public PhpVerseContentHandler(Tour tour, Path file, PhpVerseRuntime runtime) {
        this.tour = tour;
        this.file = file;
        this.runtime = runtime;
    }

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len,
                                 ContentConsumeListener lis) throws IOException {
        // Body upload not yet wired into PHP's $_POST.
        BayLog.debug("%s phpverse:onReadContent len=%d (ignored for now)",
                tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s phpverse:endContent file=%s", tur, file);

        // PHP eval text: include the .php file. Single-quoted absolute
        // path; backslash- and quote-escape defensively.
        String safePath = file.toString().replace("\\", "\\\\")
                                          .replace("'", "\\'");
        String script = "include '" + safePath + "';";

        boolean wroteAnything;
        try {
            wroteAnything = runtime.runScript(tur, script,
                    "phpverse:" + tur.req.uri);
        } catch (Exception e) {
            BayLog.error(e, "phpverse: runScript failed: %s", file);
            // If headers haven't gone out yet, surface the error.
            // Otherwise the response is partially sent and we just close
            // it best-effort.
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PhpVerse execution failed: " + e.getMessage());
        }

        if (!wroteAnything) {
            // PHP produced no output; emit a zero-byte 200 so the tour
            // can complete normally.
            tur.res.headers.setStatus(HttpStatus.OK);
            tur.res.headers.setContentType("text/html; charset=UTF-8");
            tur.res.headers.setContentLength(0);
            tur.res.setConsumeListener((l, r) -> { /* no-op */ });
            tur.res.sendHeaders(tur.tourId);
        }
        tur.res.endResContent(tur.tourId);
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s phpverse:onAbort", tur);
        return true;
    }
}
