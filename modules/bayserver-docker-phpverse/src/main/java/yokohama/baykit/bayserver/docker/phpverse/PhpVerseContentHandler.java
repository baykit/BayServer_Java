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
 * synchronously runs the requested .php file via the embedded libphp
 * runtime and writes the output to the tour response.
 *
 * <p>Mirrors {@code FileContentHandler}'s shape: ignore inbound body
 * bytes (PHP's {@code $_POST} support is M-future), and on
 * {@code onEndReqContent} dispatch to PHP. Synchronous because libphp
 * exec is blocking; the grand agent thread runs PHP inline and then
 * sends the response in one shot.
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
        // Body upload not yet wired into PHP's $_POST; just acknowledge.
        // Future M will route this to the request_info.request_body stream.
        BayLog.debug("%s phpverse:onReadContent len=%d (ignored for now)",
                tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s phpverse:endContent file=%s", tur, file);

        // PHP eval text. Single-quoted absolute path -> PHP includes the file
        // in its current request scope. Single-quote escape of any quote
        // chars in the path defends against tour-side injection.
        String safePath = file.toString().replace("\\", "\\\\")
                                          .replace("'", "\\'");
        String script = "include '" + safePath + "';";

        byte[] bytes;
        try {
            bytes = runtime.runScript(script, "phpverse:" + tur.req.uri);
        } catch (Exception e) {
            BayLog.error(e, "phpverse: runScript failed: %s", file);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PhpVerse execution failed: " + e.getMessage());
        }

        tur.res.headers.setStatus(HttpStatus.OK);
        tur.res.headers.setContentType("text/html; charset=UTF-8");
        tur.res.headers.setContentLength(bytes.length);

        // Required: TourRes.consumed() calls resConsumeListener when the
        // ship's async write completes. Without setConsumeListener, large
        // responses (~1 MB) that don't fit a single buffer write trigger
        // a Sink "Consume listener is null" because ship-side write
        // completes after we'd already left the handler.
        // We don't need to resume anything (PHP exec was synchronous and
        // the body is already fully buffered), so the listener is a no-op.
        tur.res.setConsumeListener((len, resume) -> { /* no-op */ });

        tur.res.sendHeaders(tur.tourId);
        tur.res.sendResContent(tur.tourId, bytes, 0, bytes.length);
        tur.res.endResContent(tur.tourId);
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s phpverse:onAbort", tur);
        return true;
    }
}
