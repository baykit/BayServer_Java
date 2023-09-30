package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.KeyVal;

import static yokohama.baykit.bayserver.docker.http.h2.HeaderBlock.HeaderOp.OverloadKnownHeader;

public class HeaderBlockAnalyzer {

    String name, value;
    String method, path;
    String scheme;
    String status;

    public void clear() {
        name = null;
        value = null;
        method = null;
        path = null;
        scheme = null;
        status = null;
    }

    public void analyzeHeaderBlock(HeaderBlock blk, HeaderTable tbl) throws ProtocolException {
        clear();
        switch(blk.op) {
            case Index: {
                KeyVal kv = tbl.get(blk.index);
                if(kv == null)
                    throw new ProtocolException("Invalid header index: " + blk.index);
                name = kv.name;
                value = kv.value;
                break;
            }

            case KnownHeader:
            case OverloadKnownHeader: {
                KeyVal kv = tbl.get(blk.index);
                if(kv == null)
                    throw new ProtocolException("Invalid header index: " + blk.index);
                name = kv.name;
                value = blk.value;
                if(blk.op == OverloadKnownHeader)
                    tbl.insert(name, value);
                break;
            }

            case NewHeader: {
                name = blk.name;
                value = blk.value;
                tbl.insert(name, value);
                break;
            }

            case UnknownHeader: {
                name = blk.name;
                value = blk.value;
                break;
            }

            case UpdateDynamicTableSize: {
                tbl.setSize(blk.size);
                break;
            }

            default:
                throw new IllegalStateException();
        }

        if(name != null && name.charAt(0) == ':') {
            switch(name) {
                case HeaderTable.PSEUDO_HEADER_AUTHORITY:
                    name = "host";
                    break;

                case HeaderTable.PSEUDO_HEADER_METHOD:
                    this.method = value;
                    break;

                case HeaderTable.PSEUDO_HEADER_PATH:
                    this.path = value;
                    break;

                case HeaderTable.PSEUDO_HEADER_SCHEME:
                    this.scheme = value;
                    break;

                case HeaderTable.PSEUDO_HEADER_STATUS:
                    this.status = value;
            }
        }
    }

}
