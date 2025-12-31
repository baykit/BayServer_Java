package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.util.SimpleBuffer;

import java.io.IOException;
import java.util.ArrayList;

/**
 * HPack
 *   https://datatracker.ietf.org/doc/html/rfc7541
 *
 *
 */
public class HeaderBlockRenderer {

    SimpleBuffer buf;

    HeaderBlockRenderer(SimpleBuffer buf) {
        this.buf = buf;
    }


    public void renderHeaderBlocks(ArrayList<HeaderBlock> headerBlocks) throws IOException {

        for(HeaderBlock blk : headerBlocks) {
            renderHeaderBlock(blk);
        }
    }


    private void renderHeaderBlock(HeaderBlock blk) throws IOException {
        switch(blk.op) {
            case Index: {
                putHPackInt(blk.index, 7, 1);
                break;
            }
            case OverloadKnownHeader: {
                throw new IOException();
            }
            case NewHeader: {
                throw new IOException();
            }
            case KnownHeader: {
                putHPackInt(blk.index, 4, 0);
                putHPackString(blk.value, false);
                break;
            }
            case UnknownHeader: {
                putByte(0);
                putHPackString(blk.name, false);
                putHPackString(blk.value, false);
                break;
            }
            case UpdateDynamicTableSize: {
                throw new IOException();
            }

        }
    }



    private void putHPackInt(int val, int prefix, int head) throws IOException {
        int maxVal = 0xFF >> (8 - prefix);
        int headVal = (head  << prefix) & 0xFF;
        if(val < maxVal) {
            putByte(val | headVal);
        }
        else {
            putByte(headVal | maxVal);
            putHPackIntRest(val - maxVal);
        }
    }

    private void putHPackIntRest(int val) throws IOException {
        while(true) {
            int data = val & 0x7F;
            int nextVal = val >> 7;
            if(nextVal == 0) {
                // data end
                putByte(data);
                break;
            }
            else {
                // data continues
                putByte(data | 0x80);
                val = nextVal;
            }
        }
    }

    private void putHPackString(String value, boolean haffman) throws IOException {
        if(haffman) {
            throw new IllegalArgumentException();
        }
        else {
            putHPackInt(value.length(), 7, 0);
            putBytes(value.getBytes());
        }
    }

    private void putByte(int val) {
        buf.put((byte) val);
    }

    private void putBytes(byte[] data) {
        buf.put(data);
    }



}
