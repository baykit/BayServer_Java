package yokohama.baykit.bayserver.docker.phpverse;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Process-singleton libphp.so embedding runtime for {@link PhpVerseDocker}.
 *
 * <p>{@link #init} runs once on plan parse: dlopen + patch ub_write +
 * php_embed_init (then close the auto-started request).
 *
 * <p>{@link #runScript} is called per request from any grand agent
 * thread. First call on a new thread registers it with TSRM via
 * {@code ts_resource_ex(0, NULL)}. Subsequent calls cycle the
 * per-request engine state via
 * {@code php_request_startup} / {@code _shutdown}, mirroring
 * php-fpm's per-request boundary.
 *
 * <p><b>Output path:</b> PHP's {@code echo} is streamed directly to
 * {@code Tour.res.sendResContent} from inside the {@code ub_write}
 * upcall via a {@link ThreadLocal} reference to the active tour.
 * Headers are sent lazily on the first chunk. This avoids buffering
 * the full body in Java heap, which would be 3-5 extra megabyte-scale
 * copies on a 1 MB response.
 */
public class PhpVerseRuntime {

    private static final Linker LINKER = Linker.nativeLinker();
    /** Offset of {@code name} (= first field, char*) within
     *  {@code sapi_module_struct}. opcache compares this against a
     *  hard-coded whitelist (apache/fastcgi/fpm-fcgi/frankenphp/etc.);
     *  "embed" isn't on it, so we patch the name to "fpm-fcgi" before
     *  php_embed_init so accel_post_startup sees a supported SAPI and
     *  flips accel_startup_ok = true. PHP code that checks
     *  php_sapi_name() will see "fpm-fcgi" instead of "embed", which is
     *  closer to the truth anyway (= we behave like an FCGI worker
     *  pool, just in-process). */
    private static final long NAME_OFFSET = 0;
    /** Offset of {@code ub_write} within {@code sapi_module_struct} on
     *  64-bit Linux. */
    private static final long UB_WRITE_OFFSET = 48;

    /** Per-thread mutable context: 1 ThreadLocal lookup per ub_write
     *  upcall instead of 3 separate ones, no Boolean autoboxing, and the
     *  same object is reused across requests on the same grand agent. */
    private static class ReqCtx {
        Tour tour;
        boolean headersSent;
        IOException error;
        boolean tsrmRegistered;
    }

    private static final ThreadLocal<ReqCtx> CTX =
            ThreadLocal.withInitial(ReqCtx::new);

    private final Path libPhpPath;
    private Arena arena;
    private MethodHandle phpRequestStartup;
    private MethodHandle phpRequestShutdown;
    private MethodHandle zendEvalString;
    private MethodHandle tsResourceEx;

    public PhpVerseRuntime(String libPhpPath) {
        this.libPhpPath = Path.of(libPhpPath);
    }

    /** One-time process bootstrap. Loads libphp, patches ub_write, calls
     *  php_embed_init, then closes the auto-started request so each
     *  per-request lifecycle is started fresh on its own thread. */
    public void init() {
        BayLog.info("PhpVerseRuntime: loading %s", libPhpPath);

        arena = Arena.ofShared();
        SymbolLookup lib = SymbolLookup.libraryLookup(libPhpPath, arena);

        // 1. patch php_embed_module.{name, ub_write}
        //    - name : "embed" -> "fpm-fcgi" so opcache's accel_find_sapi
        //             accepts us (= embed is not on its whitelist).
        //    - ub_write : default fwrite(stdout) -> Java upcall that
        //                 streams bytes to tour.res.sendResContent.
        MemorySegment embedModule = lib.find("php_embed_module")
                .orElseThrow(() -> new RuntimeException(
                        "php_embed_module symbol not found in " + libPhpPath))
                .reinterpret(UB_WRITE_OFFSET + 8, arena, null);

        // Allocate the spoofed name in the same process-lifetime arena
        // so its pointer stays valid forever.
        MemorySegment fpmFcgiName = cString(arena, "fpm-fcgi");
        embedModule.set(ValueLayout.ADDRESS, NAME_OFFSET, fpmFcgiName);

        try {
            MethodHandle javaUbWrite = MethodHandles.lookup().findStatic(
                    PhpVerseRuntime.class, "ubWriteCallback",
                    MethodType.methodType(long.class,
                            MemorySegment.class, long.class));
            MemorySegment ubWriteStub = LINKER.upcallStub(javaUbWrite,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                    arena);
            embedModule.set(ValueLayout.ADDRESS, UB_WRITE_OFFSET, ubWriteStub);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("upcall stub setup failed", e);
        }

        // 2. resolve C calls.
        // Note: tried Linker.Option.critical(false) on
        // php_request_startup / ts_resource_ex (= safe because they
        // don't trigger upcalls). Result was noise-level (+/- ~3% per
        // size) -- our per-request downcall budget is only ~300 ns
        // total, dwarfed by libphp's per-request cost. Reverted to keep
        // the call sites simple. Cannot apply to zend_eval_string or
        // php_request_shutdown anyway: both trigger our ub_write upcall
        // (eval -> echo, shutdown -> flush of PHP's 4 KB default ob
        // buffer for short responses).
        MethodHandle phpEmbedInit = downcall(lib, "php_embed_init",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        phpRequestStartup = downcall(lib, "php_request_startup",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
        phpRequestShutdown = downcall(lib, "php_request_shutdown",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        zendEvalString = downcall(lib, "zend_eval_string",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        tsResourceEx = downcall(lib, "ts_resource_ex",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // 3. bring PHP up; close the auto-started request so
        //    per-request boundaries start fresh per-thread.
        try {
            int rc = (int) phpEmbedInit.invoke(0, MemorySegment.NULL);
            if (rc != 0) {
                throw new RuntimeException("php_embed_init returned " + rc);
            }
            phpRequestShutdown.invoke(MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException("PhpVerse init failed", t);
        }

        BayLog.info("PhpVerseRuntime: ready (libphp loaded, SAPI started)");
    }

    /**
     * Run a PHP snippet on the current thread. Output is streamed to
     * {@code tour.res} via the ub_write upcall as PHP echoes; this
     * method does not return a body buffer.
     *
     * @return true if any output was produced (= headers were sent),
     *         false if PHP wrote nothing (caller should send a 0-byte
     *         response).
     */
    public boolean runScript(Tour tour, String phpCode, String label)
            throws IOException {
        ReqCtx ctx = CTX.get();
        try {
            // Lazy per-thread TSRM registration: first call on a new
            // grand-agent thread allocates its TLS pool. Subsequent calls
            // are no-ops.
            if (!ctx.tsrmRegistered) {
                tsResourceEx.invoke(0, MemorySegment.NULL);
                ctx.tsrmRegistered = true;
            }

            // Set up per-request streaming context.
            ctx.tour = tour;
            ctx.headersSent = false;
            ctx.error = null;

            // Per-request engine state (= the php-fpm-equivalent boundary).
            phpRequestStartup.invoke();
            try (Arena reqArena = Arena.ofConfined()) {
                MemorySegment code = cString(reqArena, phpCode);
                MemorySegment name = cString(reqArena, label);
                int rc = (int) zendEvalString.invoke(
                        code, MemorySegment.NULL, name);
                if (rc != 0) {
                    BayLog.warn("zend_eval_string returned %d for %s",
                            rc, label);
                }
            } finally {
                phpRequestShutdown.invoke(MemorySegment.NULL);
            }

            // Surface any IOException stashed by the ub_write upcall.
            if (ctx.error != null) throw ctx.error;

            return ctx.headersSent;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable t) {
            throw new RuntimeException(
                    "PhpVerse runScript failed: " + label, t);
        } finally {
            // Clear per-request fields so a leaked Tour reference doesn't
            // pin memory between requests. tsrmRegistered stays sticky.
            ctx.tour = null;
            ctx.error = null;
        }
    }

    /**
     * ub_write target. Called from inside libphp during
     * {@code zend_eval_string} when PHP code executes {@code echo} or
     * {@code print}. Streams the bytes directly to the active tour's
     * response without staging them in a Java heap buffer.
     */
    public static long ubWriteCallback(MemorySegment str, long length) {
        ReqCtx ctx = CTX.get();
        Tour tour = ctx.tour;
        if (tour == null) {
            // ub_write fired outside of a request context (= e.g. on
            // module shutdown). Discard.
            return length;
        }

        try {
            // Lazy header send on the first chunk. We don't know the
            // total content length up front, so omit Content-Length;
            // BayServer / nginx will use chunked / connection-close
            // framing as appropriate.
            if (!ctx.headersSent) {
                tour.res.headers.setStatus(HttpStatus.OK);
                tour.res.headers.setContentType("text/html; charset=UTF-8");
                tour.res.setConsumeListener((l, r) -> { /* no-op */ });
                tour.res.sendHeaders(tour.tourId);
                ctx.headersSent = true;
            }

            int len = (int) length;
            MemorySegment data = str.reinterpret(length);
            byte[] bytes = data.toArray(ValueLayout.JAVA_BYTE);
            tour.res.sendResContent(tour.tourId, bytes, 0, len);
            return length;
        } catch (IOException e) {
            // Stash, surface after eval returns.
            if (ctx.error == null) ctx.error = e;
            return -1;
        } catch (Throwable t) {
            BayLog.error(t, "ub_write upcall failed");
            return -1;
        }
    }

    private static MethodHandle downcall(SymbolLookup lib, String name,
                                          FunctionDescriptor desc) {
        return LINKER.downcallHandle(
                lib.find(name).orElseThrow(() ->
                        new RuntimeException("symbol not found: " + name)),
                desc);
    }

    private static MemorySegment cString(Arena arena, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(b.length + 1);
        MemorySegment.copy(b, 0, seg, ValueLayout.JAVA_BYTE, 0, b.length);
        seg.set(ValueLayout.JAVA_BYTE, b.length, (byte) 0);
        return seg;
    }
}
