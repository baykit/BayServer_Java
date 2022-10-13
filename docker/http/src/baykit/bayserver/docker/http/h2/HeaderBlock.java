package baykit.bayserver.docker.http.h2;

import java.io.IOException;

/**
 * HPack
 *   https://datatracker.ietf.org/doc/html/rfc7541
 *
 *
 */
public class HeaderBlock {
    public enum HeaderOp {
        Index,
        OverloadKnownHeader,
        NewHeader,
        KnownHeader,
        UnknownHeader,
        UpdateDynamicTableSize,
    }
    
    public HeaderOp op;
    public int index;
    public String name;
    public String value;
    public int size;

    @Override
    public String toString() {
        return op + " index=" + index + " name=" + name + " value=" + value + " size=" + size;
    }
    
    public static void pack(HeaderBlock blk, H2Packet.H2DataAccessor acc) throws IOException {
        switch(blk.op) {
            case Index: {
                acc.putHPackInt(blk.index, 7, 1);
                break;
            }
            case OverloadKnownHeader: {
                throw new IllegalStateException();
            }
            case NewHeader: {
                throw new IllegalStateException();
            }
            case KnownHeader: {
                acc.putHPackInt(blk.index, 4, 0);
                acc.putHPackString(blk.value, false);
                break;
            }
            case UnknownHeader: {
                acc.putByte(0);
                acc.putHPackString(blk.name, false);
                acc.putHPackString(blk.value, false);
                break;
            }
            case UpdateDynamicTableSize: {
                throw new IllegalStateException();
            }
                
        }
    }

    public static HeaderBlock unpack(H2Packet.H2DataAccessor acc) throws IOException {
        HeaderBlock blk = new HeaderBlock();
        int index = acc.getByte();
        //BayServer.debug("index: " + index);
        boolean indexHeaderField = (index & 0x80) != 0;
        if(indexHeaderField) {
            // index header field
            /**
             *   0   1   2   3   4   5   6   7
             * +---+---+---+---+---+---+---+---+
             * | 1 |        Index (7+)         |
             * +---+---------------------------+
             */
            blk.op = HeaderOp.Index;
            blk.index = index & 0x7F;
        }
        else {
            // literal header field
            boolean updateIndex = (index & 0x40) != 0;
            if(updateIndex) {
                index = index & 0x3F;
                boolean overloadIndex = index != 0;
                if(overloadIndex) {
                    // known header name
                    if(index == 0x3F) {
                        index = index + acc.getHPackIntRest();
                    }
                    blk.op = HeaderOp.OverloadKnownHeader;
                    blk.index = index;
                    
                    /**
                     *      0   1   2   3   4   5   6   7
                     *    +---+---+---+---+---+---+---+---+
                     *    | 0 | 1 |      Index (6+)       |
                     *    +---+---+-----------------------+
                     *    | H |     Value Length (7+)     |
                     *    +---+---------------------------+
                     *    | Value String (Length octets)  |
                     *    +-------------------------------+
                     */
                    blk.value = acc.getHPackString();
                }
                else {
                    // new header name
                    /**
                     *   0   1   2   3   4   5   6   7
                     * +---+---+---+---+---+---+---+---+
                     * | 0 | 1 |           0           |
                     * +---+---+-----------------------+
                     * | H |     Name Length (7+)      |
                     * +---+---------------------------+
                     * |  Name String (Length octets)  |
                     * +---+---------------------------+
                     * | H |     Value Length (7+)     |
                     * +---+---------------------------+
                     * | Value String (Length octets)  |
                     * +-------------------------------+
                     */
                    blk.op = HeaderOp.NewHeader;
                    blk.name = acc.getHPackString();
                    blk.value = acc.getHPackString();
                }
            }
            else {
                boolean updateDynamicTableSize = (index & 0x20) != 0;
                if(updateDynamicTableSize) {
                    /**
                     *   0   1   2   3   4   5   6   7
                     * +---+---+---+---+---+---+---+---+
                     * | 0 | 0 | 1 |   Max size (5+)   |
                     * +---+---------------------------+
                     */
                    int size = index & 0x1f;
                    if(size == 0x1f) {
                        size = size + acc.getHPackIntRest();
                    }
                    blk.op = HeaderOp.UpdateDynamicTableSize;
                    blk.size = size;
                }
                else {
                    // not update index
                    index = (index & 0xF);
                    if (index != 0) {
                        /**
                         *   0   1   2   3   4   5   6   7
                         * +---+---+---+---+---+---+---+---+
                         * | 0 | 0 | 0 | 0 |  Index (4+)   |
                         * +---+---+-----------------------+
                         * | H |     Value Length (7+)     |
                         * +---+---------------------------+
                         * | Value String (Length octets)  |
                         * +-------------------------------+
                         *
                         * OR
                         *
                         *   0   1   2   3   4   5   6   7
                         * +---+---+---+---+---+---+---+---+
                         * | 0 | 0 | 0 | 1 |  Index (4+)   |
                         * +---+---+-----------------------+
                         * | H |     Value Length (7+)     |
                         * +---+---------------------------+
                         * | Value String (Length octets)  |
                         * +-------------------------------+
                         */
                        if (index == 0xF) {
                            index = index + acc.getHPackIntRest();
                        }
                        blk.op = HeaderOp.KnownHeader;
                        blk.index = index;
                        blk.value = acc.getHPackString();
                    } else {
                        // literal header field
                        /**
                         *   0   1   2   3   4   5   6   7
                         * +---+---+---+---+---+---+---+---+
                         * | 0 | 0 | 0 | 0 |       0       |
                         * +---+---+-----------------------+
                         * | H |     Name Length (7+)      |
                         * +---+---------------------------+
                         * |  Name String (Length octets)  |
                         * +---+---------------------------+
                         * | H |     Value Length (7+)     |
                         * +---+---------------------------+
                         * | Value String (Length octets)  |
                         * +-------------------------------+
                         *
                         * OR
                         *
                         *   0   1   2   3   4   5   6   7
                         * +---+---+---+---+---+---+---+---+
                         * | 0 | 0 | 0 | 1 |       0       |
                         * +---+---+-----------------------+
                         * | H |     Name Length (7+)      |
                         * +---+---------------------------+
                         * |  Name String (Length octets)  |
                         * +---+---------------------------+
                         * | H |     Value Length (7+)     |
                         * +---+---------------------------+
                         * | Value String (Length octets)  |
                         * +-------------------------------+
                         */
                        blk.op = HeaderOp.UnknownHeader;
                        blk.name = acc.getHPackString();
                        blk.value = acc.getHPackString();
                    }
                }
            }
        }
        return blk;
    }
    


}
