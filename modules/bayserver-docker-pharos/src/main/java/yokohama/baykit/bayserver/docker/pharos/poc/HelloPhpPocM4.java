package yokohama.baykit.bayserver.docker.pharos.poc;

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
 * M4 PoC: capture PHP {@code echo} output back into Java by replacing
 * libphp's default {@code ub_write} with a Panama upcall stub.
 *
 * <p>M3 proved that {@code php_embed_init} works and PHP can run; output
 * went straight to {@code stdout} via libphp's default {@code ub_write}
 * (which is {@code fwrite(stdout)}). For BayServer integration we need
 * the output to land in Java instead, so we can hand it to
 * {@code Tour.res.sendContent(...)}.
 *
 * <p>Approach: patch {@code php_embed_module.ub_write} (= a function
 * pointer in a global C struct exported by libphp.so) BEFORE calling
 * {@code php_embed_init}. Our Java method becomes the new ub_write.
 *
 * <p>{@code sapi_module_struct} layout (from main/SAPI.h):
 * <pre>
 *   offset 0:  char *name
 *   offset 8:  char *pretty_name
 *   offset 16: int (*startup)(...)
 *   offset 24: int (*shutdown)(...)
 *   offset 32: int (*activate)(void)
 *   offset 40: int (*deactivate)(void)
 *   offset 48: size_t (*ub_write)(const char *str, size_t str_length)   ★
 *   offset 56: void (*flush)(void *server_context)
 *   ...
 * </pre>
 */
public class HelloPhpPocM4 {

    private static final Linker LINKER = Linker.nativeLinker();

    /** Offset of {@code ub_write} within {@code sapi_module_struct} on
     *  64-bit Linux. Six prior fields, all pointer-sized -&gt; 6 * 8 = 48. */
    private static final long UB_WRITE_OFFSET = 48;

    /** Captures everything PHP wrote via {@code echo}/{@code print}. */
    private static final StringBuilder captured = new StringBuilder();

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            System.err.println("usage: HelloPhpPocM4 <path/to/libphp.so>");
            System.exit(1);
        }
        Path libPath = Path.of(args[0]);

        try (Arena arena = Arena.ofConfined()) {

            SymbolLookup lib = SymbolLookup.libraryLookup(libPath, arena);
            System.out.println("[m4] dlopen OK: " + libPath);

            // 1. Locate the global php_embed_module struct in libphp.so
            //    so we can patch its ub_write field. We use reinterpret() to
            //    extend the symbol's segment to cover the field range.
            MemorySegment embedModuleRaw = lib.find("php_embed_module")
                    .orElseThrow(() -> new RuntimeException(
                            "php_embed_module symbol not found"));
            MemorySegment embedModule = embedModuleRaw.reinterpret(
                    UB_WRITE_OFFSET + 8, arena, null);
            System.out.println("[m4] php_embed_module @ 0x"
                    + Long.toHexString(embedModule.address()));

            // 2. Build the Java -> C upcall stub for our ub_write.
            //    C signature: size_t (*)(const char *str, size_t length)
            MethodHandle javaUbWrite = MethodHandles.lookup()
                    .findStatic(HelloPhpPocM4.class, "ubWriteCallback",
                            MethodType.methodType(long.class,
                                    MemorySegment.class, long.class));
            MemorySegment ubWriteStub = LINKER.upcallStub(
                    javaUbWrite,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS,    // const char *str
                            ValueLayout.JAVA_LONG), // size_t length
                    arena);
            System.out.println("[m4] ub_write upcall stub @ 0x"
                    + Long.toHexString(ubWriteStub.address()));

            // 3. Patch php_embed_module.ub_write -> our stub.
            MemorySegment defaultUbWrite = embedModule.get(
                    ValueLayout.ADDRESS, UB_WRITE_OFFSET);
            System.out.println("[m4] previous ub_write @ 0x"
                    + Long.toHexString(defaultUbWrite.address())
                    + " (= libphp's default fwrite-stdout)");
            embedModule.set(ValueLayout.ADDRESS, UB_WRITE_OFFSET, ubWriteStub);
            System.out.println("[m4] patched ub_write -> Java upcall");

            // 4. Resolve the embed lifecycle calls (M3 already validated these).
            MethodHandle phpEmbedInit = downcall(lib, "php_embed_init",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle phpEmbedShutdown = downcall(lib, "php_embed_shutdown",
                    FunctionDescriptor.ofVoid());
            MethodHandle zendEvalString = downcall(lib, "zend_eval_string",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));

            // 5. Run a snippet. Multi-line + non-trivial body to make sure
            //    ub_write is called more than once (PHP buffers internally
            //    but flushes on echo boundaries).
            int initRc = (int) phpEmbedInit.invoke(0, MemorySegment.NULL);
            System.out.println("[m4] php_embed_init -> " + initRc);

            String script =
                    "echo \"line 1: PHP_VERSION=\" . PHP_VERSION . \"\\n\";"
                    + "echo \"line 2: 1+1=\" . (1+1) . \"\\n\";"
                    + "echo str_repeat(\"A\", 16) . \"\\n\";";
            MemorySegment code = cString(arena, script);
            MemorySegment name = cString(arena, "m4-poc");

            int evalRc = (int) zendEvalString.invoke(code, MemorySegment.NULL, name);
            System.out.println("[m4] zend_eval_string -> " + evalRc);

            phpEmbedShutdown.invoke();
            System.out.println("[m4] php_embed_shutdown done.");

            // 6. Verify capture.
            System.out.println();
            System.out.println("=== captured PHP output (" + captured.length()
                    + " bytes) ===");
            System.out.print(captured);
            System.out.println("=== end ===");

            if (captured.length() == 0) {
                System.err.println("FAIL: ub_write upcall was never invoked!");
                System.exit(2);
            }
            System.out.println();
            System.out.println("[m4] SUCCESS: PHP echo captured into Java.");
        }
    }

    /** ub_write upcall target. PHP calls this with a C string + length;
     *  we copy it into Java memory and accumulate. */
    public static long ubWriteCallback(MemorySegment str, long length) {
        MemorySegment data = str.reinterpret(length);
        byte[] bytes = data.toArray(ValueLayout.JAVA_BYTE);
        captured.append(new String(bytes, StandardCharsets.UTF_8));
        return length;
    }

    private static MethodHandle downcall(SymbolLookup lib, String name,
                                          FunctionDescriptor desc) {
        MemorySegment sym = lib.find(name)
                .orElseThrow(() -> new RuntimeException("symbol: " + name));
        return LINKER.downcallHandle(sym, desc);
    }

    private static MemorySegment cString(Arena arena, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(b.length + 1);
        MemorySegment.copy(b, 0, seg, ValueLayout.JAVA_BYTE, 0, b.length);
        seg.set(ValueLayout.JAVA_BYTE, b.length, (byte) 0);
        return seg;
    }
}
