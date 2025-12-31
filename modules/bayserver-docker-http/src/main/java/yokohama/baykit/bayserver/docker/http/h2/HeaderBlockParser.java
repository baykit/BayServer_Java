package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.docker.http.h2.huffman.HTree;

import java.util.ArrayList;

/**
 * HPack
 *   https://datatracker.ietf.org/doc/html/rfc7541
 *
 *
 */
public class HeaderBlockParser {

    byte[] buf;
    int start;
    int pos;
    int len;

    HeaderBlockParser(byte[] buf, int start, int len) {
        this.buf = buf;
        this.start = start;
        this.pos = 0;
        this.len = len;
    }


    public ArrayList<HeaderBlock> parseHeaderBlocks() {

        ArrayList<HeaderBlock> headerBlocks = new ArrayList<>();

        while(pos < len) {
            HeaderBlock blk = parseHeaderBlock();
            if(BayLog.isTraceMode())
                BayLog.trace("h2: header block read: " + blk);
            headerBlocks.add(blk);
        }

        return headerBlocks;
    }


    private HeaderBlock parseHeaderBlock()  {

        HeaderBlock blk = new HeaderBlock();

        int index = getByte();
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
            blk.op = HeaderBlock.HeaderOp.Index;
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
                        index = index + getHPackIntRest();
                    }
                    blk.op = HeaderBlock.HeaderOp.OverloadKnownHeader;
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
                    blk.value = getHPackString();
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
                    blk.op = HeaderBlock.HeaderOp.NewHeader;
                    blk.name = getHPackString();
                    blk.value = getHPackString();
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
                        size = size + getHPackIntRest();
                    }
                    blk.op = HeaderBlock.HeaderOp.UpdateDynamicTableSize;
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
                            index = index + getHPackIntRest();
                        }
                        blk.op = HeaderBlock.HeaderOp.KnownHeader;
                        blk.index = index;
                        blk.value = getHPackString();
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
                        blk.op = HeaderBlock.HeaderOp.UnknownHeader;
                        blk.name = getHPackString();
                        blk.value = getHPackString();
                    }
                }
            }
        }

        return blk;
    }


    private int getHPackInt(int prefix, int[] head) {
        int maxVal = 0xFF >> (8 - prefix);

        int firstByte = getByte();
        int firstVal = firstByte & maxVal;
        head[0] = firstByte >> prefix;
        if(firstVal != maxVal) {
            return firstVal;
        }
        else {
            return maxVal + getHPackIntRest();
        }
    }

    private int getHPackIntRest() {
        int rest = 0;
        for(int i = 0; ; i++) {
            int data = getByte();
            boolean cont = (data & 0x80) != 0;
            int value = (data & 0x7F);
            rest = rest + (value << (i * 7));
            if(!cont)
                break;
        }
        return rest;
    }

    private String getHPackString()  {
        int isHuffman[] = new int[1];
        int len = getHPackInt(7, isHuffman);
        byte[] data = new byte[len];
        getBytes(data);
        if(isHuffman[0] == 1) {
            // Huffman
            /*
            for(int i = 0; i < data.length; i++) {
                String bits = "00000000" + Integer.toString(data[i] & 0xFF, 2);
                BayServer.debug(bits.substring(bits.length() - 8));
            }
            */
            return HTree.decode(data);
        }
        else {
            // ASCII
            return new String(data);
        }
    }

    private int getByte() {
        if(pos >= len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return buf[start + pos++] & 0xff;
    }

    private void getBytes(byte[] buf) {
        System.arraycopy(this.buf, this.start + this.pos, buf, 0, buf.length);
        pos += buf.length;
    }
}
