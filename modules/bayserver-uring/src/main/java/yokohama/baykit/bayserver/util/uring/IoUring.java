package yokohama.baykit.bayserver.util.uring;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.HashMap;

import static yokohama.baykit.bayserver.util.uring.IoUringConstants.*;

/**
 * Low-level io_uring ring buffer management using Panama FFI.
 * Manages SQ (submission) and CQ (completion) rings via mmap'd memory.
 */
public class IoUring implements AutoCloseable {

    ////////////////////////////////////////////
    // io_uring_params struct layout
    ////////////////////////////////////////////
    static final StructLayout PARAMS_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("sq_entries"),        // 0
            ValueLayout.JAVA_INT.withName("cq_entries"),        // 4
            ValueLayout.JAVA_INT.withName("flags"),             // 8
            ValueLayout.JAVA_INT.withName("sq_thread_cpu"),     // 12
            ValueLayout.JAVA_INT.withName("sq_thread_idle"),    // 16
            ValueLayout.JAVA_INT.withName("features"),          // 20
            ValueLayout.JAVA_INT.withName("wq_fd"),             // 24
            MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT).withName("resv"),  // 28
            // sq_off (40 bytes at offset 40)
            ValueLayout.JAVA_INT.withName("sq_off_head"),       // 40
            ValueLayout.JAVA_INT.withName("sq_off_tail"),       // 44
            ValueLayout.JAVA_INT.withName("sq_off_ring_mask"),  // 48
            ValueLayout.JAVA_INT.withName("sq_off_ring_entries"), // 52
            ValueLayout.JAVA_INT.withName("sq_off_flags"),      // 56
            ValueLayout.JAVA_INT.withName("sq_off_dropped"),    // 60
            ValueLayout.JAVA_INT.withName("sq_off_array"),      // 64
            ValueLayout.JAVA_INT.withName("sq_off_resv1"),      // 68
            ValueLayout.JAVA_LONG.withName("sq_off_resv2"),     // 72
            // cq_off (40 bytes at offset 80)
            ValueLayout.JAVA_INT.withName("cq_off_head"),       // 80
            ValueLayout.JAVA_INT.withName("cq_off_tail"),       // 84
            ValueLayout.JAVA_INT.withName("cq_off_ring_mask"),  // 88
            ValueLayout.JAVA_INT.withName("cq_off_ring_entries"), // 92
            ValueLayout.JAVA_INT.withName("cq_off_overflow"),   // 96
            ValueLayout.JAVA_INT.withName("cq_off_cqes"),       // 100
            ValueLayout.JAVA_INT.withName("cq_off_flags"),      // 104
            ValueLayout.JAVA_INT.withName("cq_off_resv1"),      // 108
            ValueLayout.JAVA_LONG.withName("cq_off_resv2")      // 112
    );  // total: 120 bytes

    ////////////////////////////////////////////
    // io_uring_sqe struct layout (64 bytes)
    ////////////////////////////////////////////
    public static final StructLayout SQE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_BYTE.withName("opcode"),           // 0
            ValueLayout.JAVA_BYTE.withName("flags"),            // 1
            ValueLayout.JAVA_SHORT.withName("ioprio"),          // 2
            ValueLayout.JAVA_INT.withName("fd"),                // 4
            ValueLayout.JAVA_LONG.withName("off"),              // 8
            ValueLayout.JAVA_LONG.withName("addr"),             // 16
            ValueLayout.JAVA_INT.withName("len"),               // 24
            ValueLayout.JAVA_INT.withName("rw_flags"),          // 28
            ValueLayout.JAVA_LONG.withName("user_data"),        // 32
            MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_SHORT).withName("buf_index"), // 40
            MemoryLayout.sequenceLayout(1, ValueLayout.JAVA_SHORT).withName("personality"), // 46
            ValueLayout.JAVA_INT.withName("splice_fd_in"),      // 48
            MemoryLayout.sequenceLayout(12, ValueLayout.JAVA_BYTE).withName("__pad2") // 52 -> pad to 64
    ).withByteAlignment(8);

    ////////////////////////////////////////////
    // io_uring_cqe struct layout (16 bytes)
    ////////////////////////////////////////////
    public static final StructLayout CQE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("user_data"),        // 0
            ValueLayout.JAVA_INT.withName("res"),               // 8
            ValueLayout.JAVA_INT.withName("flags")              // 12
    );

    ////////////////////////////////////////////
    // VarHandles for SQE fields
    ////////////////////////////////////////////
    public static final VarHandle SQE_OPCODE    = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("opcode"));
    public static final VarHandle SQE_FLAGS     = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("flags"));
    public static final VarHandle SQE_IOPRIO    = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("ioprio"));
    public static final VarHandle SQE_FD        = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("fd"));
    public static final VarHandle SQE_OFF       = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("off"));
    public static final VarHandle SQE_ADDR      = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("addr"));
    public static final VarHandle SQE_LEN       = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("len"));
    public static final VarHandle SQE_RW_FLAGS  = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("rw_flags"));
    public static final VarHandle SQE_USER_DATA = SQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("user_data"));

    ////////////////////////////////////////////
    // VarHandles for CQE fields
    ////////////////////////////////////////////
    public static final VarHandle CQE_USER_DATA = CQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("user_data"));
    public static final VarHandle CQE_RES       = CQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("res"));
    public static final VarHandle CQE_FLAGS     = CQE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("flags"));

    ////////////////////////////////////////////
    // VarHandles for params fields
    ////////////////////////////////////////////
    private static final VarHandle PARAMS_FLAGS          = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("flags"));
    private static final VarHandle PARAMS_SQ_ENTRIES     = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_entries"));
    private static final VarHandle PARAMS_CQ_ENTRIES     = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("cq_entries"));
    private static final VarHandle PARAMS_SQ_OFF_HEAD    = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_head"));
    private static final VarHandle PARAMS_SQ_OFF_TAIL    = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_tail"));
    private static final VarHandle PARAMS_SQ_OFF_RING_MASK = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_ring_mask"));
    private static final VarHandle PARAMS_SQ_OFF_RING_ENTRIES = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_ring_entries"));
    private static final VarHandle PARAMS_SQ_OFF_FLAGS   = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_flags"));
    private static final VarHandle PARAMS_SQ_OFF_ARRAY   = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("sq_off_array"));
    private static final VarHandle PARAMS_CQ_OFF_HEAD    = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("cq_off_head"));
    private static final VarHandle PARAMS_CQ_OFF_TAIL    = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("cq_off_tail"));
    private static final VarHandle PARAMS_CQ_OFF_RING_MASK = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("cq_off_ring_mask"));
    private static final VarHandle PARAMS_CQ_OFF_CQES    = PARAMS_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("cq_off_cqes"));

    ////////////////////////////////////////////
    // Native downcall handles
    ////////////////////////////////////////////
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = LINKER.defaultLookup();

    private static final MethodHandle syscall;
    // Lightweight syscall handle without errno capture (for hot-path io_uring_enter)
    private static final MethodHandle syscallFast;
    // 6-arg variant for IORING_ENTER_EXT_ARG (timeout support)
    private static final MethodHandle syscallFast6;
    private static final MethodHandle mmap;
    private static final MethodHandle munmap;
    private static final MethodHandle closeHandle;
    private static final MethodHandle eventfdHandle;
    private static final MethodHandle eventfdWriteHandle;
    private static final StructLayout CAPTURE_STATE_LAYOUT = Linker.Option.captureStateLayout();
    private static final VarHandle ERRNO_HANDLE = CAPTURE_STATE_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("errno"));

    static {
        try {
            syscall = LINKER.downcallHandle(
                    LOOKUP.find("syscall").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_LONG,  // syscall number
                            ValueLayout.JAVA_LONG,  // arg1
                            ValueLayout.JAVA_LONG,  // arg2
                            ValueLayout.JAVA_LONG,  // arg3
                            ValueLayout.JAVA_LONG,  // arg4
                            ValueLayout.JAVA_LONG   // arg5
                    ),
                    Linker.Option.captureCallState("errno"));

            // Fast path: no errno capture — reduces Panama FFI overhead per call
            syscallFast = LINKER.downcallHandle(
                    LOOKUP.find("syscall").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_LONG,  // syscall number
                            ValueLayout.JAVA_LONG,  // arg1 (fd)
                            ValueLayout.JAVA_LONG,  // arg2 (to_submit)
                            ValueLayout.JAVA_LONG,  // arg3 (min_complete)
                            ValueLayout.JAVA_LONG,  // arg4 (flags)
                            ValueLayout.JAVA_LONG   // arg5 (sig)
                    ));

            // 6-arg variant for IORING_ENTER_EXT_ARG (adds argsz parameter)
            syscallFast6 = LINKER.downcallHandle(
                    LOOKUP.find("syscall").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_LONG,  // syscall number
                            ValueLayout.JAVA_LONG,  // arg1 (fd)
                            ValueLayout.JAVA_LONG,  // arg2 (to_submit)
                            ValueLayout.JAVA_LONG,  // arg3 (min_complete)
                            ValueLayout.JAVA_LONG,  // arg4 (flags)
                            ValueLayout.JAVA_LONG,  // arg5 (arg)
                            ValueLayout.JAVA_LONG   // arg6 (argsz)
                    ));

            mmap = LINKER.downcallHandle(
                    LOOKUP.find("mmap").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,    // addr
                            ValueLayout.JAVA_LONG,  // length
                            ValueLayout.JAVA_INT,   // prot
                            ValueLayout.JAVA_INT,   // flags
                            ValueLayout.JAVA_INT,   // fd
                            ValueLayout.JAVA_LONG   // offset
                    ));

            munmap = LINKER.downcallHandle(
                    LOOKUP.find("munmap").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,    // addr
                            ValueLayout.JAVA_LONG   // length
                    ));

            closeHandle = LINKER.downcallHandle(
                    LOOKUP.find("close").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT    // fd
                    ));

            eventfdHandle = LINKER.downcallHandle(
                    LOOKUP.find("eventfd").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,   // initval
                            ValueLayout.JAVA_INT    // flags
                    ));

            // Use write(2) syscall for eventfd_write
            eventfdWriteHandle = LINKER.downcallHandle(
                    LOOKUP.find("write").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_INT,   // fd
                            ValueLayout.ADDRESS,    // buf
                            ValueLayout.JAVA_LONG   // count
                    ));
        }
        catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    ////////////////////////////////////////////
    // Ring state
    ////////////////////////////////////////////
    private final Arena arena;
    private final int ringFd;

    // SQ ring
    private final MemorySegment sqRing;
    private final long sqRingSize;
    private final MemorySegment sqHead;      // pointer to head (int)
    private final MemorySegment sqTail;      // pointer to tail (int)
    private final int sqRingMask;
    private final int sqEntries;
    private final MemorySegment sqFlagsPtr;  // pointer to flags (int)
    private final MemorySegment sqArray;     // SQ ring index array

    // CQ ring
    private final MemorySegment cqRing;
    private final long cqRingSize;
    private final MemorySegment cqHead;      // pointer to head (int)
    private final MemorySegment cqTail;      // pointer to tail (int)
    private final int cqRingMask;
    private final int cqEntries;
    private final MemorySegment cqCqes;      // CQE array base

    // SQE array
    private final MemorySegment sqes;
    private final long sqesSize;

    // eventfd for wakeup
    private final int eventFd;
    private final MemorySegment eventFdBuf;  // 8-byte buffer for eventfd read/write

    // Timeout support for submitAndWait
    private final MemorySegment timespec;       // __kernel_timespec (16 bytes)
    private final MemorySegment geteventsArg;   // io_uring_getevents_arg (24 bytes)

    private boolean closed;

    /**
     * Initialize io_uring with the specified number of entries.
     */
    public IoUring(int entries) throws IOException {
        this.arena = Arena.ofShared();

        try {
            // Allocate io_uring_params
            MemorySegment params = arena.allocate(PARAMS_LAYOUT);
            params.fill((byte) 0);

            // io_uring_setup syscall
            long ret = ioUringSetup(entries, params);
            if (ret < 0) {
                throw new IOException("io_uring_setup failed: errno=" + (-ret));
            }
            this.ringFd = (int) ret;

            // Read params
            this.sqEntries = (int) PARAMS_SQ_ENTRIES.get(params, 0L);
            this.cqEntries = (int) PARAMS_CQ_ENTRIES.get(params, 0L);

            int sqOff_head        = (int) PARAMS_SQ_OFF_HEAD.get(params, 0L);
            int sqOff_tail        = (int) PARAMS_SQ_OFF_TAIL.get(params, 0L);
            int sqOff_ringMask    = (int) PARAMS_SQ_OFF_RING_MASK.get(params, 0L);
            int sqOff_ringEntries = (int) PARAMS_SQ_OFF_RING_ENTRIES.get(params, 0L);
            int sqOff_flags       = (int) PARAMS_SQ_OFF_FLAGS.get(params, 0L);
            int sqOff_array       = (int) PARAMS_SQ_OFF_ARRAY.get(params, 0L);

            int cqOff_head        = (int) PARAMS_CQ_OFF_HEAD.get(params, 0L);
            int cqOff_tail        = (int) PARAMS_CQ_OFF_TAIL.get(params, 0L);
            int cqOff_ringMask    = (int) PARAMS_CQ_OFF_RING_MASK.get(params, 0L);
            int cqOff_cqes        = (int) PARAMS_CQ_OFF_CQES.get(params, 0L);

            // mmap SQ ring
            this.sqRingSize = sqOff_array + (long) sqEntries * Integer.BYTES;
            this.sqRing = mmapRing(sqRingSize, ringFd, IORING_OFF_SQ_RING);

            this.sqHead     = sqRing.asSlice(sqOff_head, Integer.BYTES);
            this.sqTail     = sqRing.asSlice(sqOff_tail, Integer.BYTES);
            this.sqRingMask = sqRing.get(ValueLayout.JAVA_INT, sqOff_ringMask);
            this.sqFlagsPtr = sqRing.asSlice(sqOff_flags, Integer.BYTES);
            this.sqArray    = sqRing.asSlice(sqOff_array, (long) sqEntries * Integer.BYTES);

            // mmap CQ ring
            this.cqRingSize = cqOff_cqes + (long) cqEntries * CQE_LAYOUT.byteSize();
            this.cqRing = mmapRing(cqRingSize, ringFd, IORING_OFF_CQ_RING);

            this.cqHead     = cqRing.asSlice(cqOff_head, Integer.BYTES);
            this.cqTail     = cqRing.asSlice(cqOff_tail, Integer.BYTES);
            this.cqRingMask = cqRing.get(ValueLayout.JAVA_INT, cqOff_ringMask);
            this.cqCqes     = cqRing.asSlice(cqOff_cqes, (long) cqEntries * CQE_LAYOUT.byteSize());

            // mmap SQE array
            this.sqesSize = (long) sqEntries * SQE_LAYOUT.byteSize();
            this.sqes = mmapRing(sqesSize, ringFd, IORING_OFF_SQES);

            // Create eventfd for wakeup
            this.eventFd = createEventFd();
            this.eventFdBuf = arena.allocate(8, 8);

            // Pre-allocate reusable buffers
            this.capturedState = arena.allocate(CAPTURE_STATE_LAYOUT);
            this.eventFdReadBuf = arena.allocate(8, 8);

            // Pre-allocate timeout structs for submitAndWait
            // __kernel_timespec: { int64 tv_sec, int64 tv_nsec } = 16 bytes
            this.timespec = arena.allocate(16, 8);
            // io_uring_getevents_arg: { u64 sigmask, u32 sigmask_sz, u32 pad, u64 ts } = 24 bytes
            this.geteventsArg = arena.allocate(24, 8);
            this.geteventsArg.fill((byte) 0);
            // Set ts pointer to timespec
            this.geteventsArg.set(ValueLayout.JAVA_LONG, 16, timespec.address());

        }
        catch (IOException e) {
            arena.close();
            throw e;
        }
        catch (Throwable e) {
            arena.close();
            throw new IOException("Failed to initialize io_uring", e);
        }
    }

    /**
     * Get an available SQE slot.
     * @throws IOException if the SQ ring is full
     */
    public MemorySegment getSqe() throws IOException {
        int head = sqHead.get(ValueLayout.JAVA_INT, 0);
        int tail = sqTail.get(ValueLayout.JAVA_INT, 0);

        if (tail - head >= sqEntries) {
            throw new IOException("SQ ring is full");
        }

        int index = tail & sqRingMask;
        MemorySegment sqe = sqes.asSlice((long) index * SQE_LAYOUT.byteSize(), SQE_LAYOUT.byteSize());

        // Clear the SQE
        sqe.fill((byte) 0);

        // Set the SQ array entry
        sqArray.set(ValueLayout.JAVA_INT, (long) index * Integer.BYTES, index);

        // Advance tail
        sqTail.set(ValueLayout.JAVA_INT, 0, tail + 1);

        return sqe;
    }

    /**
     * Submit queued SQEs and optionally wait for completions.
     * @param toSubmit number of SQEs to submit (0 means submit all pending)
     * @param minComplete minimum completions to wait for (0 for non-blocking)
     * @param flags io_uring_enter flags
     * @return number of SQEs submitted, or negative errno
     */
    public int submit(int toSubmit, int minComplete, int flags) throws IOException {
        try {
            // Use fast downcall without errno capture for the hot path
            long ret = (long) syscallFast.invoke(
                    __NR_io_uring_enter,
                    (long) ringFd,
                    (long) toSubmit,
                    (long) minComplete,
                    (long) flags,
                    0L  // sigset (NULL)
            );
            if (ret == -1) {
                // Retry with errno capture to get detailed error info
                long ret2 = (long) syscall.invoke(
                        capturedState,
                        __NR_io_uring_enter,
                        (long) ringFd,
                        (long) toSubmit,
                        (long) minComplete,
                        (long) flags,
                        0L
                );
                int errno = (ret2 == -1) ? (int) ERRNO_HANDLE.get(capturedState, 0L) : 0;
                throw new IOException("io_uring_enter failed: errno=" + errno
                        + " (fd=" + ringFd + " toSubmit=" + toSubmit
                        + " minComplete=" + minComplete + " flags=0x" + Integer.toHexString(flags) + ")");
            }
            return (int) ret;
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("io_uring_enter failed", e);
        }
    }

    /**
     * Submit all pending SQEs without waiting.
     */
    public int submit() throws IOException {
        int pending = pendingSqeCount();
        if (pending == 0)
            return 0;
        return submit(pending, 0, 0);
    }

    /**
     * Submit all pending SQEs and wait for at least one completion, with timeout.
     * @param timeoutSec timeout in seconds (0 for non-blocking)
     */
    public int submitAndWait(int timeoutSec) throws IOException {
        int pending = pendingSqeCount();
        if (timeoutSec <= 0) {
            return submit(Math.max(pending, 0), 1, IORING_ENTER_GETEVENTS);
        }

        // Set timeout
        timespec.set(ValueLayout.JAVA_LONG, 0, (long) timeoutSec);  // tv_sec
        timespec.set(ValueLayout.JAVA_LONG, 8, 0L);                 // tv_nsec

        int flags = IORING_ENTER_GETEVENTS | IORING_ENTER_EXT_ARG;
        try {
            long ret = (long) syscallFast6.invoke(
                    __NR_io_uring_enter,
                    (long) ringFd,
                    (long) Math.max(pending, 0),
                    1L,                             // min_complete
                    (long) flags,
                    geteventsArg.address(),          // arg
                    24L                              // argsz = sizeof(io_uring_getevents_arg)
            );
            if (ret == -1) {
                // ETIME is expected on timeout — not an error
                long ret2 = (long) syscall.invoke(
                        capturedState,
                        __NR_io_uring_enter,
                        (long) ringFd,
                        (long) Math.max(pending, 0),
                        1L,
                        (long) flags,
                        geteventsArg.address(),
                        24L
                );
                int errno = (ret2 == -1) ? (int) ERRNO_HANDLE.get(capturedState, 0L) : 0;
                if (errno == 62) {  // ETIME
                    return 0;
                }
                throw new IOException("io_uring_enter failed: errno=" + errno);
            }
            return (int) ret;
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("io_uring_enter with timeout failed", e);
        }
    }

    /**
     * Peek at the next CQE without consuming it. Returns null if no CQE available.
     */
    public MemorySegment peekCqe() {
        int head = cqHead.get(ValueLayout.JAVA_INT, 0);
        int tail = cqTail.get(ValueLayout.JAVA_INT, 0);

        if (head == tail) {
            return null; // No CQE available
        }

        int index = head & cqRingMask;
        return cqCqes.asSlice((long) index * CQE_LAYOUT.byteSize(), CQE_LAYOUT.byteSize());
    }

    /**
     * Advance the CQ head by one, consuming the current CQE.
     */
    public void advanceCq() {
        int head = cqHead.get(ValueLayout.JAVA_INT, 0);
        cqHead.set(ValueLayout.JAVA_INT, 0, head + 1);
    }

    /**
     * Returns the number of pending (not yet submitted) SQEs.
     */
    public int pendingSqeCount() {
        int head = sqHead.get(ValueLayout.JAVA_INT, 0);
        int tail = sqTail.get(ValueLayout.JAVA_INT, 0);
        return tail - head;
    }

    /**
     * Returns the number of available CQEs.
     */
    public int availableCqeCount() {
        int head = cqHead.get(ValueLayout.JAVA_INT, 0);
        int tail = cqTail.get(ValueLayout.JAVA_INT, 0);
        return tail - head;
    }

    /**
     * Get the eventfd file descriptor for wakeup.
     */
    public int eventFd() {
        return eventFd;
    }

    /**
     * Write to eventfd to trigger wakeup.
     */
    public void wakeup() throws IOException {
        eventFdBuf.set(ValueLayout.JAVA_LONG, 0, 1L);
        try {
            long ret = (long) eventfdWriteHandle.invoke(eventFd, eventFdBuf, 8L);
            if (ret < 0) {
                throw new IOException("eventfd write failed: ret=" + ret);
            }
        }
        catch (Throwable e) {
            throw new IOException("eventfd write failed", e);
        }
    }

    /**
     * Get the ring file descriptor.
     */
    public int ringFd() {
        return ringFd;
    }

    /**
     * Allocate native memory within the ring's arena.
     */
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return arena.allocate(byteSize, byteAlignment);
    }

    /**
     * Allocate native memory within the ring's arena.
     */
    public MemorySegment allocate(long byteSize) {
        return arena.allocate(byteSize);
    }

    @Override
    public void close() throws IOException {
        if (closed)
            return;
        closed = true;

        try {
            nativeClose(eventFd);
            munmapRegion(sqes, sqesSize);
            munmapRegion(cqRing, cqRingSize);
            munmapRegion(sqRing, sqRingSize);
            nativeClose(ringFd);
        }
        catch (Throwable e) {
            throw new IOException("Failed to close io_uring", e);
        }
        finally {
            arena.close();
        }
    }

    /**
     * Release pooled buffers for a closed fd.
     * Call this when a connection is closed to allow the HashMap entry to be removed.
     * (Native memory remains in the arena until IoUring.close(), but HashMap entries are cleaned up.)
     */
    public void releaseFdBuffers(int fd) {
        recvBufPool.remove(fd);
        sendBufPool.remove(fd);
    }

    ////////////////////////////////////////////
    // High-level API (hides MemorySegment)
    ////////////////////////////////////////////

    // Reusable capture state for submit() (used synchronously, safe to reuse)
    private final MemorySegment capturedState;

    // Reusable eventfd read buffer (only one eventfd read in-flight at a time)
    private final MemorySegment eventFdReadBuf;

    // Per-fd buffer pools (only one recv/send in-flight per fd at a time)
    private final HashMap<Integer, MemorySegment> recvBufPool = new HashMap<>();
    private final HashMap<Integer, MemorySegment> sendBufPool = new HashMap<>();

    // Per-server-fd accept buffer pool (sockaddr + addrlen)
    private final HashMap<Integer, MemorySegment> acceptSockaddrPool = new HashMap<>();
    private final HashMap<Integer, MemorySegment> acceptAddrLenPool = new HashMap<>();

    // Reusable CQE result array (single-threaded polling)
    private final long[] cqeResult = new long[2];

    /**
     * Prepare an ACCEPT operation.
     */
    public void prepareAccept(int serverFd, long userData) throws IOException {
        MemorySegment sqe = getSqe();

        MemorySegment sockaddr = acceptSockaddrPool.get(serverFd);
        MemorySegment addrLen = acceptAddrLenPool.get(serverFd);
        if (sockaddr == null) {
            sockaddr = SockAddr.allocateStorage(arena);
            addrLen = SockAddr.allocateAddrLen(arena);
            acceptSockaddrPool.put(serverFd, sockaddr);
            acceptAddrLenPool.put(serverFd, addrLen);
        } else {
            // Reset addrlen for reuse
            addrLen.set(ValueLayout.JAVA_INT, 0, IoUringConstants.SIZEOF_SOCKADDR_STORAGE);
        }

        IoUringSqe.prepAccept(sqe, serverFd, sockaddr, addrLen, userData);
    }

    /**
     * Prepare a RECV operation with a native buffer of the given size.
     */
    public void prepareRecv(int fd, int bufSize, long userData) throws IOException {
        MemorySegment sqe = getSqe();

        // Reuse recv buffer per fd (only one recv in-flight per fd)
        MemorySegment buf = recvBufPool.get(fd);
        if (buf == null || buf.byteSize() < bufSize) {
            buf = arena.allocate(bufSize);
            recvBufPool.put(fd, buf);
        }

        IoUringSqe.prepRecv(sqe, fd, buf, bufSize, userData);
    }

    /**
     * Prepare a SEND operation, copying data from the ByteBuffer.
     */
    public void prepareSend(int fd, ByteBuffer srcBuf, long userData) throws IOException {
        MemorySegment sqe = getSqe();

        int remaining = srcBuf.remaining();

        // Reuse send buffer per fd, grow if needed (only one send in-flight per fd)
        MemorySegment buf = sendBufPool.get(fd);
        if (buf == null || buf.byteSize() < remaining) {
            buf = arena.allocate(remaining);
            sendBufPool.put(fd, buf);
        }

        // Bulk copy from ByteBuffer to native memory
        MemorySegment srcSeg = MemorySegment.ofBuffer(srcBuf);
        MemorySegment.copy(srcSeg, ValueLayout.JAVA_BYTE, srcBuf.position(), buf, ValueLayout.JAVA_BYTE, 0, remaining);

        IoUringSqe.prepSend(sqe, fd, buf, remaining, userData);
    }

    /**
     * Prepare a CONNECT operation.
     */
    public void prepareConnect(int fd, InetSocketAddress addr, long userData) throws IOException {
        MemorySegment sqe = getSqe();

        MemorySegment sockaddr = SockAddr.toNative(arena, addr);
        IoUringSqe.prepConnect(sqe, fd, sockaddr, SockAddr.nativeSize(addr), userData);
    }

    /**
     * Prepare a CLOSE operation.
     */
    public void prepareClose(int fd, long userData) throws IOException {
        MemorySegment sqe = getSqe();

        IoUringSqe.prepClose(sqe, fd, userData);
    }

    /**
     * Prepare an eventfd READ operation for wakeup.
     */
    public void prepareEventFdRead(long userData) throws IOException {
        MemorySegment sqe = getSqe();

        IoUringSqe.prepRead(sqe, eventFd, eventFdReadBuf, 8, userData);
    }

    /**
     * Poll for a completion. Returns null if no CQE available.
     * Returns long[]{userData, res}.
     */
    public long[] pollCompletion() {
        MemorySegment cqe = peekCqe();
        if (cqe == null)
            return null;

        cqeResult[0] = (long) CQE_USER_DATA.get(cqe, 0L);
        cqeResult[1] = (int) CQE_RES.get(cqe, 0L);
        advanceCq();

        return cqeResult;
    }

    /**
     * Copy received data into the given ByteBuffer.
     * Uses the per-fd recv buffer (only one recv in-flight per fd).
     */
    public void copyRecvData(int fd, ByteBuffer dst, int nBytes) {
        MemorySegment buf = recvBufPool.get(fd);
        if (buf == null)
            return;

        dst.clear();
        if (nBytes > 0) {
            // Bulk copy: native memory -> ByteBuffer
            MemorySegment dstSeg = MemorySegment.ofBuffer(dst);
            MemorySegment.copy(buf, ValueLayout.JAVA_BYTE, 0, dstSeg, ValueLayout.JAVA_BYTE, dst.position(), nBytes);
            dst.position(dst.position() + nBytes);
            dst.flip();
        }
        else {
            dst.limit(0);
        }
    }

    /**
     * Get fd from a Java NIO Channel (delegates to NativeFd).
     */
    public static int getFdFromChannel(Channel ch) throws IOException {

        return NativeFd.getFd(ch);
    }

    /**
     * Checks if io_uring is supported on this system.
     */
    public static boolean isSupported() {
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment params = tempArena.allocate(PARAMS_LAYOUT);
            params.fill((byte) 0);
            MemorySegment capturedState = tempArena.allocate(CAPTURE_STATE_LAYOUT);
            long ret = (long) syscall.invoke(
                    capturedState,
                    __NR_io_uring_setup,
                    1L,
                    params.address(),
                    0L, 0L, 0L
            );
            if (ret >= 0) {
                closeHandle.invoke((int) ret);
                return true;
            }
            return false;
        }
        catch (Throwable e) {
            return false;
        }
    }

    ////////////////////////////////////////////
    // Private helpers
    ////////////////////////////////////////////

    private long ioUringSetup(int entries, MemorySegment params) throws Throwable {
        MemorySegment capturedState = arena.allocate(CAPTURE_STATE_LAYOUT);
        long ret = (long) syscall.invoke(
                capturedState,
                __NR_io_uring_setup,
                (long) entries,
                params.address(),
                0L, 0L, 0L
        );
        if (ret == -1) {
            int errno = (int) ERRNO_HANDLE.get(capturedState, 0L);
            return -errno;
        }
        return ret;
    }

    private MemorySegment mmapRing(long size, int fd, long offset) throws Throwable {
        MemorySegment result = (MemorySegment) mmap.invoke(
                MemorySegment.NULL,
                size,
                PROT_READ | PROT_WRITE,
                MAP_SHARED | MAP_POPULATE,
                fd,
                offset
        );
        long addr = result.address();
        if (addr == -1L || addr == 0L) {
            throw new IOException("mmap failed for io_uring ring (offset=" + offset + ")");
        }
        // Reinterpret as full size
        return result.reinterpret(size);
    }

    private void munmapRegion(MemorySegment segment, long size) throws Throwable {
        munmap.invoke(segment, size);
    }

    private void nativeClose(int fd) throws Throwable {
        closeHandle.invoke(fd);
    }

    private int createEventFd() throws IOException {
        try {
            int fd = (int) eventfdHandle.invoke(0, EFD_NONBLOCK | EFD_CLOEXEC);
            if (fd < 0) {
                throw new IOException("eventfd creation failed");
            }
            return fd;
        }
        catch (IOException e) {
            throw e;
        }
        catch (Throwable e) {
            throw new IOException("eventfd creation failed", e);
        }
    }
}
