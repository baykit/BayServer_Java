package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.util.KeyVal;

import static yokohama.baykit.bayserver.docker.http.h2.HeaderBlock.HeaderOp.OverloadKnownHeader;

public class HeaderBlockAnalyzer {

    String name, value;
    // The original header name before any renaming (e.g. :authority -> host).
    // Needed by pseudo-header validation that must distinguish :authority
    // from a literal "host" header the client might also send.
    String rawName;
    String method, path;
    String scheme;
    String status;
    // True iff rawName started with ':' (i.e. this was a pseudo-header).
    boolean pseudo;

    public void clear() {
        name = null;
        value = null;
        rawName = null;
        method = null;
        path = null;
        scheme = null;
        status = null;
        pseudo = false;
    }

    public void analyzeHeaderBlock(HeaderBlock blk, HeaderTable tbl) throws ProtocolException {
        clear();
        switch(blk.op) {
            case Index: {
                // RFC 7541 § 2.3.3: indexed representation must reference a
                // valid entry; out-of-range indices are a decoding error.
                KeyVal kv;
                try {
                    kv = tbl.get(blk.index);
                } catch (IllegalArgumentException e) {
                    throw new ProtocolException("Invalid header index: " + blk.index);
                }
                if(kv == null)
                    throw new ProtocolException("Invalid header index: " + blk.index);
                name = kv.name;
                value = kv.value;
                break;
            }

            case KnownHeader:
            case OverloadKnownHeader: {
                KeyVal kv;
                try {
                    kv = tbl.get(blk.index);
                } catch (IllegalArgumentException e) {
                    throw new ProtocolException("Invalid header index: " + blk.index);
                }
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

        rawName = name;
        pseudo = name != null && !name.isEmpty() && name.charAt(0) == ':';

        if(pseudo) {
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
