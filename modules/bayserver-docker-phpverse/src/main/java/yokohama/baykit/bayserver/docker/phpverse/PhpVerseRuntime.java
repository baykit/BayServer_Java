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
    /** Offset of {@code ub_write} within {@code sapi_module_struct} on
     *  64-bit Linux. */
    private static final long UB_WRITE_OFFSET = 48;

    /** Active Tour for the calling thread, set by {@link #runScript}.
     *  The ub_write upcall reads this to know where the bytes go. */
    private static final ThreadLocal<Tour> CURRENT_TOUR = new ThreadLocal<>();

    /** Per-request flag: have we already sent headers? Set on the first
     *  ub_write call so subsequent chunks of the same response just
     *  forward content without re-sending the header line. */
    private static final ThreadLocal<Boolean> HEADERS_SENT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** First IOException seen during a request's ub_write stream; the
     *  upcall cannot throw back into native PHP, so we stash the error
     *  here and surface it after the eval returns. */
    private static final ThreadLocal<IOException> STREAM_ERROR =
            new ThreadLocal<>();

    /** Has this thread been registered with TSRM yet? */
    private static final ThreadLocal<Boolean> TSRM_REGISTERED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

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

        // 1. patch php_embed_module.ub_write -> Java upcall
        MemorySegment embedModule = lib.find("php_embed_module")
                .orElseThrow(() -> new RuntimeException(
                        "php_embed_module symbol not found in " + libPhpPath))
                .reinterpret(UB_WRITE_OFFSET + 8, arena, null);

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

        // 2. resolve C calls
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
        try {
            // Lazy per-thread TSRM registration: first call on a new
            // grand-agent thread allocates its TLS pool. Subsequent calls
            // are no-ops.
            if (!TSRM_REGISTERED.get()) {
                tsResourceEx.invoke(0, MemorySegment.NULL);
                TSRM_REGISTERED.set(Boolean.TRUE);
            }

            // Set up per-request streaming context.
            CURRENT_TOUR.set(tour);
            HEADERS_SENT.set(Boolean.FALSE);
            STREAM_ERROR.remove();

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
            IOException err = STREAM_ERROR.get();
            if (err != null) throw err;

            return HEADERS_SENT.get();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable t) {
            throw new RuntimeException(
                    "PhpVerse runScript failed: " + label, t);
        } finally {
            CURRENT_TOUR.remove();
            HEADERS_SENT.remove();
            STREAM_ERROR.remove();
        }
    }

    /**
     * ub_write target. Called from inside libphp during
     * {@code zend_eval_string} when PHP code executes {@code echo} or
     * {@code print}. Streams the bytes directly to the active tour's
     * response without staging them in a Java heap buffer.
     */
    public static long ubWriteCallback(MemorySegment str, long length) {
        Tour tour = CURRENT_TOUR.get();
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
            if (!HEADERS_SENT.get()) {
                tour.res.headers.setStatus(HttpStatus.OK);
                tour.res.headers.setContentType("text/html; charset=UTF-8");
                tour.res.setConsumeListener((l, r) -> { /* no-op */ });
                tour.res.sendHeaders(tour.tourId);
                HEADERS_SENT.set(Boolean.TRUE);
            }

            int len = (int) length;
            MemorySegment data = str.reinterpret(length);
            byte[] bytes = data.toArray(ValueLayout.JAVA_BYTE);
            tour.res.sendResContent(tour.tourId, bytes, 0, len);
            return length;
        } catch (IOException e) {
            // Stash, surface after eval returns.
            if (STREAM_ERROR.get() == null) STREAM_ERROR.set(e);
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
