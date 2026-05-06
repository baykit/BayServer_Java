package yokohama.baykit.bayserver.docker.pharos;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.ClubBase;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.URLDecoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BayServer docker that runs PHP via {@code libphp.so} embedded in the
 * BayServer JVM (= same lifecycle as php-fpm, but in-process and without
 * the FCGI envelope or fork+exec overhead).
 *
 * <p>Plan usage:
 * <pre>
 *   [club *.php]
 *       docker phpVerse
 *       libPhpPath /path/to/libphp.so      # required, ZTS embed build
 *       iniPath    /path/to/php.ini        # optional (TODO M+: passed to module_startup)
 * </pre>
 *
 * <p>The runtime is initialised once per JVM (= shared across all towns
 * and clubs in this BayServer instance). Re-initialising libphp would
 * conflict with PHP's "single SAPI per process" assumption.
 *
 * <p><b>Shape:</b>
 * <pre>
 *   plan parse  -> init()              [main thread]
 *                  - dlopen libphp
 *                  - patch ub_write -> Java upcall
 *                  - php_embed_init (auto-request closed)
 *
 *   each request -> arrive(Tour)       [grand agent thread]
 *                  - resolve docroot/uri -> .php file
 *                  - install PharosContentHandler
 *
 *                  -> handler.onEndReqContent(Tour)  [same grand agent]
 *                     - first-time TSRM register on this thread
 *                     - php_request_startup
 *                     - zend_eval_string("include '<file>';")
 *                     - php_request_shutdown
 *                     - send headers + body to tour.res
 * </pre>
 */
public class PharosDocker extends ClubBase {

    /** Path to the ZTS embed build of {@code libphp.so}. Required. */
    public String libPhpPath;

    /** Optional php.ini path. Currently unused (libphp picks its own
     *  default). M+: pass to {@code php_module_startup} via the
     *  {@code php_ini_path_override} global. */
    public String iniPath;

    /** Shared runtime, lazily initialised on first plan parse. */
    private static final AtomicReference<PharosRuntime> RUNTIME =
            new AtomicReference<>();

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if (StringUtil.empty(libPhpPath)) {
            throw new ConfigException(elm.fileName, elm.lineNo,
                    "PharosDocker requires 'libPhpPath' "
                            + "(/path/to/libphp.so, ZTS embed build)");
        }

        // Initialise runtime once per JVM. Concurrent init() races (unlikely
        // since plan parse is single-thread, but cheap to guard) lose here:
        // first writer wins, others observe the existing instance.
        if (RUNTIME.get() == null) {
            synchronized (PharosDocker.class) {
                if (RUNTIME.get() == null) {
                    PharosRuntime rt = new PharosRuntime(libPhpPath);
                    rt.init();
                    RUNTIME.set(rt);
                }
            }
        }

        BayLog.info("PharosDocker ready: libPhpPath=%s ini=%s",
                libPhpPath, iniPath);
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            case "libphppath":
                libPhpPath = kv.value;
                break;
            case "inipath":
                iniPath = kv.value;
                break;
            default:
                return super.initKeyVal(kv);
        }
        return true;
    }

    @Override
    public void arrive(Tour tur) throws HttpException {
        // 1. Resolve the .php file path the same way FileDocker does:
        //    docroot + (uri minus town prefix), URL-decoded, query-stripped.
        String relPath = tur.req.rewrittenURI != null
                ? tur.req.rewrittenURI : tur.req.uri;
        if (!StringUtil.empty(tur.town.name())) {
            relPath = relPath.substring(tur.town.name().length());
        }
        int q = relPath.indexOf('?');
        if (q != -1) {
            relPath = relPath.substring(0, q);
        }
        try {
            relPath = URLDecoder.decode(relPath, tur.req.charset());
        } catch (Exception e) {
            BayLog.error("Cannot decode path: %s: %s", relPath, e);
        }

        Path file = Paths.get(tur.town.location(), relPath);
        if (!Files.isRegularFile(file)) {
            throw new HttpException(HttpStatus.NOT_FOUND, file.toString());
        }

        PharosRuntime rt = RUNTIME.get();
        if (rt == null) {
            // Should not happen: init() guarantees RUNTIME is set before
            // arrive() is reachable.
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "PharosRuntime not initialised");
        }

        PharosContentHandler handler =
                new PharosContentHandler(tur, file, rt);
        tur.req.setReqContentHandler(handler);
    }
}
