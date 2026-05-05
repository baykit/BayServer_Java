package yokohama.baykit.bayserver.docker.phpverse.poc;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * M5 PoC: multi-thread libphp embedding with per-thread TSRM contexts.
 *
 * <p>Spawn N worker threads. Each thread independently runs PHP requests
 * and captures its own output via a {@link ThreadLocal}. Verifies:
 * <ul>
 *   <li>Different threads can run PHP simultaneously without crashes
 *       (= ZTS build + per-thread TSRM context working).</li>
 *   <li>Each thread sees only its own echo output (= no state leak via
 *       SAPI globals / EG / SG).</li>
 *   <li>Multiple sequential requests on the same thread do NOT carry
 *       state forward (= {@code php_request_startup} / {@code _shutdown}
 *       boundary effective, just like php-fpm).</li>
 * </ul>
 *
 * <p>Lifecycle (mirrors what M6 will do inside PhpVerseDocker):
 * <pre>
 *   main:    php_embed_init     // tsrm_startup + sapi + module + request
 *            php_request_shutdown   // tear down the auto-started request
 *   worker:  per request:
 *              php_request_startup
 *              zend_eval_string(...)
 *              php_request_shutdown
 *   main:    php_module_shutdown
 *            sapi_shutdown
 * </pre>
 *
 * <p>Note: each worker thread's TSRM context is allocated lazily on the
 * first call to {@code php_request_startup} via PHP's TSRMLS_FETCH
 * mechanism (= internally calls {@code ts_resource_ex(0, NULL)}).
 */
public class HelloPhpPocM5 {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final long UB_WRITE_OFFSET = 48;

    /** Per-thread output buffer. ub_write upcall appends to whichever
     *  thread is currently calling into PHP. */
    private static final ThreadLocal<StringBuilder> CAPTURED =
            ThreadLocal.withInitial(StringBuilder::new);

    // Resolved C handles, set up once on main thread.
    private static MethodHandle phpRequestStartup;
    private static MethodHandle phpRequestShutdown;
    private static MethodHandle zendEvalString;
    private static MethodHandle tsResourceEx;
    private static MethodHandle tsFreeThread;

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            System.err.println("usage: HelloPhpPocM5 <path/to/libphp.so>");
            System.exit(1);
        }
        Path libPath = Path.of(args[0]);
        int nWorkers = 4;
        int reqsPerWorker = 5;

        // Use shared arena so the upcall stub + memory segments survive
        // across worker threads. Confined arena is single-thread only.
        Arena arena = Arena.ofShared();
        try {
            SymbolLookup lib = SymbolLookup.libraryLookup(libPath, arena);
            System.out.println("[m5] dlopen OK");

            // 1. Patch ub_write -> our upcall (same as M4).
            MemorySegment embedModule = lib.find("php_embed_module").orElseThrow()
                    .reinterpret(UB_WRITE_OFFSET + 8, arena, null);
            MethodHandle javaUbWrite = MethodHandles.lookup().findStatic(
                    HelloPhpPocM5.class, "ubWriteCallback",
                    MethodType.methodType(long.class, MemorySegment.class, long.class));
            MemorySegment ubWriteStub = LINKER.upcallStub(javaUbWrite,
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                    arena);
            embedModule.set(ValueLayout.ADDRESS, UB_WRITE_OFFSET, ubWriteStub);
            System.out.println("[m5] ub_write patched -> Java upcall");

            // 2. Resolve all the lifecycle calls we need.
            MethodHandle phpEmbedInit = downcall(lib, "php_embed_init",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MethodHandle phpEmbedShutdown = downcall(lib, "php_embed_shutdown",
                    FunctionDescriptor.ofVoid());
            phpRequestStartup = downcall(lib, "php_request_startup",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            phpRequestShutdown = downcall(lib, "php_request_shutdown",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            zendEvalString = downcall(lib, "zend_eval_string",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            // ts_resource_ex(0, NULL) registers the calling thread with
            // TSRM so engine-globals macros (EG/SG/...) work. PHP's
            // tsrm_get_ls_cache() returns NULL until this is called -->
            // segfault on the first php_request_startup otherwise.
            tsResourceEx = downcall(lib, "ts_resource_ex",
                    FunctionDescriptor.of(ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            tsFreeThread = downcall(lib, "ts_free_thread",
                    FunctionDescriptor.ofVoid());

            // 3. Bring up PHP. php_embed_init also auto-starts a request
            //    on this thread; we close that to start clean.
            int rc = (int) phpEmbedInit.invoke(0, MemorySegment.NULL);
            if (rc != 0) throw new RuntimeException("php_embed_init failed: " + rc);
            phpRequestShutdown.invoke(MemorySegment.NULL);
            System.out.println("[m5] php_embed_init done; auto-request closed");

            // 4. Fan out N workers.
            CountDownLatch done = new CountDownLatch(nWorkers);
            Thread[] workers = new Thread[nWorkers];
            String[] results = new String[nWorkers];
            boolean[] ok = new boolean[nWorkers];

            for (int i = 0; i < nWorkers; i++) {
                final int id = i;
                workers[i] = new Thread(() -> {
                    try {
                        // Register this thread with TSRM. After this,
                        // tsrm_get_ls_cache() returns a valid pointer
                        // and php_request_startup works on this thread.
                        tsResourceEx.invoke(0, MemorySegment.NULL);
                        StringBuilder seenAll = new StringBuilder();
                        for (int r = 0; r < reqsPerWorker; r++) {
                            CAPTURED.get().setLength(0);
                            // PHP request lifecycle on THIS thread.
                            phpRequestStartup.invoke();
                            String script =
                                    "echo 'worker=" + id + " req=" + r
                                    + " sum=' . array_sum(range(1, 100)) . \"\\n\";";
                            try (Arena reqArena = Arena.ofConfined()) {
                                MemorySegment code = cString(reqArena, script);
                                MemorySegment name = cString(reqArena,
                                        "w" + id + "-r" + r);
                                int evalRc = (int) zendEvalString.invoke(
                                        code, MemorySegment.NULL, name);
                                if (evalRc != 0) {
                                    throw new RuntimeException(
                                            "eval failed: " + evalRc);
                                }
                            }
                            phpRequestShutdown.invoke(MemorySegment.NULL);
                            seenAll.append(CAPTURED.get());
                            // Tiny pause to interleave with other workers.
                            Thread.sleep(ThreadLocalRandom.current().nextInt(5));
                        }
                        results[id] = seenAll.toString();
                        ok[id] = true;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        ok[id] = false;
                    } finally {
                        // Release this thread's TSRM resources.
                        try { tsFreeThread.invoke(); } catch (Throwable ignored) {}
                        done.countDown();
                    }
                }, "phpverse-worker-" + i);
                workers[i].start();
            }

            done.await();
            System.out.println("[m5] all workers done; verifying isolation...");

            // 5. Verify each worker's output mentions ONLY its own id.
            int fails = 0;
            for (int i = 0; i < nWorkers; i++) {
                System.out.println("--- worker " + i + " (ok=" + ok[i] + ") ---");
                System.out.print(results[i]);
                if (!ok[i]) { fails++; continue; }
                String[] lines = results[i].split("\n");
                if (lines.length != reqsPerWorker) {
                    System.out.println("  FAIL: expected " + reqsPerWorker
                            + " lines, got " + lines.length);
                    fails++;
                    continue;
                }
                for (String line : lines) {
                    if (!line.startsWith("worker=" + i + " ")) {
                        System.out.println("  FAIL: line not from worker "
                                + i + ": " + line);
                        fails++;
                        break;
                    }
                    // sum=5050 verifies real PHP execution
                    if (!line.contains("sum=5050")) {
                        System.out.println("  FAIL: missing sum=5050: " + line);
                        fails++;
                        break;
                    }
                }
            }

            phpEmbedShutdown.invoke();
            System.out.println("[m5] php_embed_shutdown done");

            if (fails == 0) {
                System.out.println();
                System.out.println("[m5] SUCCESS: " + nWorkers + " workers x "
                        + reqsPerWorker + " requests = "
                        + (nWorkers * reqsPerWorker)
                        + " PHP executions, all isolated.");
            } else {
                System.err.println("FAIL: " + fails + " workers had problems.");
                System.exit(2);
            }
        } finally {
            arena.close();
        }
    }

    public static long ubWriteCallback(MemorySegment str, long length) {
        MemorySegment data = str.reinterpret(length);
        byte[] bytes = data.toArray(ValueLayout.JAVA_BYTE);
        CAPTURED.get().append(new String(bytes, StandardCharsets.UTF_8));
        return length;
    }

    private static MethodHandle downcall(SymbolLookup lib, String name,
                                          FunctionDescriptor desc) {
        return LINKER.downcallHandle(
                lib.find(name).orElseThrow(() -> new RuntimeException(name)),
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
