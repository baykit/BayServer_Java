package baykit.bayserver.docker.http.h2;

import java.util.EnumSet;
import java.util.Set;

public class H2Flags {

    public static final int FLAGS_NONE = 0x0;
    public static final int FLAGS_ACK = 0x1;
    public static final int FLAGS_END_STREAM = 0x1;
    public static final int FLAGS_END_HEADERS = 0x4;
    public static final int FLAGS_PADDED = 0x8;
    public static final int FLAGS_PRIORITY = 0x20;

    public int flags;

    public H2Flags() {
        this(FLAGS_NONE);
    }

    public H2Flags(int flags) {
        this.flags = flags;
    }



    public boolean getFlag(int flag) {
        return (flags & flag) != 0;
    }

    public void setFlag(int flag, boolean val) {
        if(val)
            flags |= flag;
        else
            flags &= ~flag;
    }

    public boolean ack() {
        return getFlag(FLAGS_ACK);
    }

    public void setAck(boolean isAck) {
        setFlag(FLAGS_ACK, isAck);
    }

    public boolean endStream() {
        return getFlag(FLAGS_END_STREAM);
    }

    public void setEndStream(boolean isEndStream) {
        setFlag(FLAGS_END_STREAM, isEndStream);
    }

    public boolean endHeaders() {
        return getFlag(FLAGS_END_HEADERS);
    }

    public void setEndHeaders(boolean isEndHeaders) {
        setFlag(FLAGS_END_HEADERS, isEndHeaders);
    }

    public boolean padded() {
        return getFlag(FLAGS_PADDED);
    }

    public void setPadded(boolean isPadded) {
        setFlag(FLAGS_PADDED, isPadded);
    }

    public boolean priority() {
        return getFlag(FLAGS_PRIORITY);
    }

    public void setPriority(boolean flag) {
        setFlag(FLAGS_PRIORITY, flag);
    }

    @Override
    public String toString() {
        return Integer.toString(flags);
    }
}
