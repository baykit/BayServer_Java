package yokohama.baykit.bayserver.util.uring;

import java.lang.foreign.MemorySegment;

import static yokohama.baykit.bayserver.util.uring.IoUring.*;
import static yokohama.baykit.bayserver.util.uring.IoUringConstants.*;

/**
 * Helper methods to prepare io_uring SQE (Submission Queue Entry) for various operations.
 *
 * SQE layout (64 bytes):
 * ┌──────────┬────┬───────┬──────┬──────┬──────┬─────┬───────────┬──────────┐
 * │ opcode   │flag│ioprio │  fd  │ off  │ addr │ len │ rw_flags  │user_data │...
 * │ (1byte)  │(1) │ (2)   │ (4)  │ (8)  │ (8)  │ (4) │   (4)     │   (8)    │
 * └──────────┴────┴───────┴──────┴──────┴──────┴─────┴───────────┴──────────┘
 *
 * Each method fills the required fields for its operation:
 *
 *   Method        opcode    fd          addr          off          len       Purpose
 *   ------------- --------- ----------- ------------- ------------ --------- ---------------
 *   prepAccept    ACCEPT    server fd   sockaddr buf  addrlen ptr  -         Accept connection
 *   prepRecv      RECV      socket fd   recv buf      -            buf size  Receive data
 *   prepSend      SEND      socket fd   send buf      -            buf size  Send data
 *   prepConnect   CONNECT   socket fd   sockaddr buf  addrlen      -         Initiate connect
 *   prepClose     CLOSE     fd          -             -            -         Close fd
 *   prepRead      READ      fd          read buf      -1 (cur pos) buf size  Read (eventfd etc.)
 *   prepPollAdd   POLL_ADD  fd          -             -            poll mask Monitor readiness
 */
public final class IoUringSqe {

    private IoUringSqe() {}

    /**
     * Prepare an ACCEPT operation.
     * @param sqe      SQE memory segment
     * @param fd       server socket fd
     * @param addr     sockaddr buffer (or NULL)
     * @param addrlen  pointer to socklen_t (or NULL)
     * @param userData user data for CQE correlation
     */
    public static void prepAccept(MemorySegment sqe, int fd, MemorySegment addr, MemorySegment addrlen, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_ACCEPT);
        SQE_FD.set(sqe, 0L, fd);
        SQE_ADDR.set(sqe, 0L, addr != null ? addr.address() : 0L);
        SQE_OFF.set(sqe, 0L, addrlen != null ? addrlen.address() : 0L);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a RECV operation.
     * @param sqe      SQE memory segment
     * @param fd       socket fd
     * @param buf      receive buffer
     * @param len      buffer length
     * @param userData user data for CQE correlation
     */
    public static void prepRecv(MemorySegment sqe, int fd, MemorySegment buf, int len, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_RECV);
        SQE_FD.set(sqe, 0L, fd);
        SQE_ADDR.set(sqe, 0L, buf.address());
        SQE_LEN.set(sqe, 0L, len);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a SEND operation.
     * @param sqe      SQE memory segment
     * @param fd       socket fd
     * @param buf      send buffer
     * @param len      buffer length
     * @param userData user data for CQE correlation
     */
    public static void prepSend(MemorySegment sqe, int fd, MemorySegment buf, int len, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_SEND);
        SQE_FD.set(sqe, 0L, fd);
        SQE_ADDR.set(sqe, 0L, buf.address());
        SQE_LEN.set(sqe, 0L, len);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a CONNECT operation.
     * @param sqe      SQE memory segment
     * @param fd       socket fd
     * @param addr     sockaddr buffer
     * @param addrlen  sockaddr length
     * @param userData user data for CQE correlation
     */
    public static void prepConnect(MemorySegment sqe, int fd, MemorySegment addr, int addrlen, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_CONNECT);
        SQE_FD.set(sqe, 0L, fd);
        SQE_ADDR.set(sqe, 0L, addr.address());
        SQE_OFF.set(sqe, 0L, (long) addrlen);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a CLOSE operation.
     * @param sqe      SQE memory segment
     * @param fd       file descriptor to close
     * @param userData user data for CQE correlation
     */
    public static void prepClose(MemorySegment sqe, int fd, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_CLOSE);
        SQE_FD.set(sqe, 0L, fd);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a READ operation (for eventfd wakeup, pipe, etc.).
     * @param sqe      SQE memory segment
     * @param fd       file descriptor
     * @param buf      read buffer
     * @param len      buffer length
     * @param userData user data for CQE correlation
     */
    public static void prepRead(MemorySegment sqe, int fd, MemorySegment buf, int len, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_READ);
        SQE_FD.set(sqe, 0L, fd);
        SQE_ADDR.set(sqe, 0L, buf.address());
        SQE_LEN.set(sqe, 0L, len);
        SQE_OFF.set(sqe, 0L, -1L);  // -1 means current file position (not applicable for eventfd)
        SQE_USER_DATA.set(sqe, 0L, userData);
    }

    /**
     * Prepare a POLL_ADD operation (for readiness monitoring).
     * @param sqe      SQE memory segment
     * @param fd       file descriptor to monitor
     * @param pollMask poll event mask (POLLIN, POLLOUT, etc.)
     * @param userData user data for CQE correlation
     */
    public static void prepPollAdd(MemorySegment sqe, int fd, int pollMask, long userData) {
        SQE_OPCODE.set(sqe, 0L, IORING_OP_POLL_ADD);
        SQE_FD.set(sqe, 0L, fd);
        SQE_RW_FLAGS.set(sqe, 0L, pollMask);
        SQE_USER_DATA.set(sqe, 0L, userData);
    }
}
