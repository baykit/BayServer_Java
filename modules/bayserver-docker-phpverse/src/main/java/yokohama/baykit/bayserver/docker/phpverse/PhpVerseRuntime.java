package yokohama.baykit.bayserver.docker.phpverse;

import yokohama.baykit.bayserver.BayLog;

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
 * <p>This class encapsulates the M3-M5 PoC findings:
 * <ul>
 *   <li>{@link #init} runs once on plan parse: dlopen + patch ub_write +
 *       php_embed_init (then close the auto-started request).</li>
 *   <li>{@link #runScript} is called per request from any grand agent
 *       thread. First call on a new thread registers it with TSRM via
 *       {@code ts_resource_ex(0, NULL)}. Subsequent calls cycle the
 *       per-request engine state via
 *       {@code php_request_startup} / {@code _shutdown}, mirroring
 *       php-fpm's per-request boundary.</li>
 * </ul>
 *
 * <p>Output capture: PHP's {@code echo} is redirected to a per-thread
 * {@link StringBuilder} via the patched {@code ub_write}. Each thread sees
 * only its own buffer (= no cross-tour leakage, verified in M5 PoC).
 */
public class PhpVerseRuntime {

    private static final Linker LINKER = Linker.nativeLinker();
    /** Offset of {@code ub_write} within {@code sapi_module_struct} on
     *  64-bit Linux. */
    private static final long UB_WRITE_OFFSET = 48;

    /** Per-thread output buffer. ub_write upcall writes here. */
    static final ThreadLocal<StringBuilder> OUTPUT =
            ThreadLocal.withInitial(StringBuilder::new);

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
     * Run a PHP snippet on the current thread, return its captured echo
     * output. Called once per HTTP request from a BayServer grand agent.
     */
    public String runScript(String phpCode, String label) {
        try {
            // Lazy per-thread TSRM registration: first call on a new
            // grand-agent thread allocates its TLS pool. Subsequent calls
            // are no-ops.
            if (!TSRM_REGISTERED.get()) {
                tsResourceEx.invoke(0, MemorySegment.NULL);
                TSRM_REGISTERED.set(Boolean.TRUE);
            }

            // Reset per-thread output buffer at request start.
            StringBuilder buf = OUTPUT.get();
            buf.setLength(0);

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

            return buf.toString();
        } catch (Throwable t) {
            throw new RuntimeException("PhpVerse runScript failed: " + label, t);
        }
    }

    /** ub_write target. PHP echo bytes -&gt; current thread's buffer. */
    public static long ubWriteCallback(MemorySegment str, long length) {
        MemorySegment data = str.reinterpret(length);
        byte[] bytes = data.toArray(ValueLayout.JAVA_BYTE);
        OUTPUT.get().append(new String(bytes, StandardCharsets.UTF_8));
        return length;
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
