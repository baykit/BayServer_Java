package yokohama.baykit.bayserver.util.uring;

/**
 * Constants for io_uring syscalls and structures (Linux x86_64).
 */
public final class IoUringConstants {

    private IoUringConstants() {}

    ////////////////////////////////////////////
    // Syscall numbers (x86_64)
    ////////////////////////////////////////////
    public static final long __NR_io_uring_setup    = 425;
    public static final long __NR_io_uring_enter    = 426;
    public static final long __NR_io_uring_register = 427;

    ////////////////////////////////////////////
    // io_uring_setup flags
    ////////////////////////////////////////////
    public static final int IORING_SETUP_IOPOLL          = 1;
    public static final int IORING_SETUP_SQPOLL          = 2;
    public static final int IORING_SETUP_SQ_AFF          = 4;
    public static final int IORING_SETUP_COOP_TASKRUN    = 0x100;   // Linux 6.0+
    public static final int IORING_SETUP_SINGLE_ISSUER   = 0x1000;  // Linux 6.0+
    public static final int IORING_SETUP_DEFER_TASKRUN   = 0x2000;  // Linux 6.1+

    ////////////////////////////////////////////
    // io_uring_enter flags
    ////////////////////////////////////////////
    public static final int IORING_ENTER_GETEVENTS  = 1;
    public static final int IORING_ENTER_SQ_WAKEUP  = 2;
    public static final int IORING_ENTER_EXT_ARG    = 8;

    ////////////////////////////////////////////
    // io_uring opcodes
    ////////////////////////////////////////////
    public static final byte IORING_OP_NOP          = 0;
    public static final byte IORING_OP_READV        = 1;
    public static final byte IORING_OP_WRITEV       = 2;
    public static final byte IORING_OP_FSYNC        = 3;
    public static final byte IORING_OP_READ_FIXED   = 4;
    public static final byte IORING_OP_WRITE_FIXED  = 5;
    public static final byte IORING_OP_POLL_ADD     = 6;
    public static final byte IORING_OP_POLL_REMOVE  = 7;
    public static final byte IORING_OP_ACCEPT       = 13;
    public static final byte IORING_OP_CONNECT      = 16;
    public static final byte IORING_OP_CLOSE        = 19;
    public static final byte IORING_OP_READ         = 22;
    public static final byte IORING_OP_WRITE        = 23;
    public static final byte IORING_OP_SEND         = 26;
    public static final byte IORING_OP_RECV         = 27;

    ////////////////////////////////////////////
    // SQE flags
    ////////////////////////////////////////////
    public static final byte IOSQE_FIXED_FILE       = 1;
    public static final byte IOSQE_IO_DRAIN         = 2;
    public static final byte IOSQE_IO_LINK          = 4;

    ////////////////////////////////////////////
    // SQ ring flags (read from sq_flags in mmap'd region)
    ////////////////////////////////////////////
    public static final int IORING_SQ_NEED_WAKEUP   = 1;

    ////////////////////////////////////////////
    // mmap offsets for io_uring_setup
    ////////////////////////////////////////////
    public static final long IORING_OFF_SQ_RING = 0L;
    public static final long IORING_OFF_CQ_RING = 0x8000000L;
    public static final long IORING_OFF_SQES    = 0x10000000L;

    ////////////////////////////////////////////
    // mmap constants
    ////////////////////////////////////////////
    public static final int PROT_READ       = 0x1;
    public static final int PROT_WRITE      = 0x2;
    public static final int MAP_SHARED      = 0x01;
    public static final int MAP_POPULATE    = 0x08000;

    ////////////////////////////////////////////
    // fcntl constants
    ////////////////////////////////////////////
    public static final int F_GETFL         = 3;
    public static final int F_SETFL         = 4;
    public static final int O_NONBLOCK      = 04000;

    ////////////////////////////////////////////
    // eventfd constants
    ////////////////////////////////////////////
    public static final int EFD_NONBLOCK    = 04000;
    public static final int EFD_CLOEXEC     = 02000000;

    ////////////////////////////////////////////
    // socket constants
    ////////////////////////////////////////////
    public static final short AF_INET       = 2;
    public static final short AF_INET6      = 10;

    ////////////////////////////////////////////
    // struct sizes
    ////////////////////////////////////////////
    public static final int SIZEOF_IO_URING_SQE     = 64;
    public static final int SIZEOF_IO_URING_CQE     = 16;
    public static final int SIZEOF_IO_URING_PARAMS  = 120;
    public static final int SIZEOF_SOCKADDR_IN      = 16;
    public static final int SIZEOF_SOCKADDR_IN6     = 28;
    public static final int SIZEOF_SOCKADDR_STORAGE = 128;
}
