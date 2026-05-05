package yokohama.baykit.bayserver.docker.phpverse.poc;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * M3 PoC: Java -> libphp.so via Project Panama (FFM API).
 *
 * <p>Loads the ZTS embed build of {@code libphp.so} and executes a PHP
 * snippet through the {@code php_embed_*} API. The output of the PHP
 * {@code echo} should appear on stdout (= libphp's default ub_write goes
 * to {@code fwrite(stdout)} which the JVM inherits).
 *
 * <p>Run with:
 * <pre>
 * java --enable-native-access=ALL-UNNAMED \
 *      -cp modules/bayserver-docker-phpverse/target/classes \
 *      yokohama.baykit.bayserver.docker.phpverse.poc.HelloPhpPoc \
 *      &lt;repo&gt;/.phpenv/versions/8.4.10-zts-embed/lib/libphp.so
 * </pre>
 *
 * <p>Success criterion: stdout contains the line emitted by PHP and the
 * JVM exits cleanly (= no segfault, no signal-handler hijack).
 *
 * <p>This PoC bypasses constructing a full {@code sapi_module_struct} by
 * leveraging libphp's bundled {@code embed} SAPI ({@code php_embed_init}
 * sets it up internally). Future milestones will replace this with a
 * BayServer-specific SAPI whose {@code ub_write} routes to
 * {@code Tour.res} via Panama upcall stubs.
 */
public class HelloPhpPoc {

    private static final Linker LINKER = Linker.nativeLinker();

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            System.err.println("usage: HelloPhpPoc <path/to/libphp.so>");
            System.exit(1);
        }
        Path libPath = Path.of(args[0]);

        // Arena that lives for the whole PoC; libphp.so symbols and our
        // C strings stay valid until close().
        try (Arena arena = Arena.ofConfined()) {

            // 1. dlopen libphp.so
            SymbolLookup lib = SymbolLookup.libraryLookup(libPath, arena);
            System.out.println("[poc] dlopen OK: " + libPath);

            // 2. Resolve the three C functions we need.
            MethodHandle phpEmbedInit = downcall(lib, "php_embed_init",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,        // int argc
                            ValueLayout.ADDRESS));       // char **argv
            MethodHandle phpEmbedShutdown = downcall(lib, "php_embed_shutdown",
                    FunctionDescriptor.ofVoid());
            MethodHandle zendEvalString = downcall(lib, "zend_eval_string",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,         // const char *str
                            ValueLayout.ADDRESS,         // zval *retval
                            ValueLayout.ADDRESS));       // const char *string_name

            // 3. php_embed_init(0, NULL) -- this internally calls
            //    sapi_startup, php_module_startup, php_request_startup.
            System.out.println("[poc] calling php_embed_init...");
            int initRc = (int) phpEmbedInit.invoke(0, MemorySegment.NULL);
            System.out.println("[poc] php_embed_init -> " + initRc
                    + " (0 = SUCCESS in PHP convention)");

            // 4. zend_eval_string("echo '...';", NULL, "embed")
            //    The echo output should land on stdout via the embed SAPI's
            //    default ub_write (= fwrite(stdout)).
            MemorySegment code = cString(arena,
                    "echo \"Hello from PHP " + phpVersionLine()
                    + " via Panama (libphp embedded)!\\n\";");
            MemorySegment name = cString(arena, "embed-poc");
            System.out.println("[poc] calling zend_eval_string...");
            System.out.println("---PHP output begin---");
            System.out.flush();
            int evalRc = (int) zendEvalString.invoke(code, MemorySegment.NULL, name);
            System.out.flush();
            System.out.println("---PHP output end---");
            System.out.println("[poc] zend_eval_string -> " + evalRc
                    + " (0 = SUCCESS)");

            // 5. php_embed_shutdown() -- request_shutdown + module_shutdown
            //    + sapi_shutdown.
            System.out.println("[poc] calling php_embed_shutdown...");
            phpEmbedShutdown.invoke();
            System.out.println("[poc] DONE.");
        }
    }

    /** Resolve a C symbol from the loaded library and return a downcall handle. */
    private static MethodHandle downcall(SymbolLookup lib, String name,
                                          FunctionDescriptor desc) {
        MemorySegment sym = lib.find(name)
                .orElseThrow(() -> new RuntimeException("symbol not found: " + name));
        return LINKER.downcallHandle(sym, desc);
    }

    /** Allocate a NUL-terminated UTF-8 C string. JDK FFM lacks a stable
     *  cross-version helper for this so we do it by hand. */
    private static MemorySegment cString(Arena arena, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(b.length + 1);
        MemorySegment.copy(b, 0, seg, ValueLayout.JAVA_BYTE, 0, b.length);
        seg.set(ValueLayout.JAVA_BYTE, b.length, (byte) 0);
        return seg;
    }

    /** Convenient label inside the echo so we can see what version libphp
     *  is actually running. The PHP_VERSION constant lives inside libphp
     *  itself, but we don't need to read it here -- a literal is fine. */
    private static String phpVersionLine() {
        return "(version reported by PHP itself: \" . PHP_VERSION . \")";
    }
}
