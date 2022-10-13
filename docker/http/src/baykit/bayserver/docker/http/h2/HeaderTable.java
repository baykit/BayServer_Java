package baykit.bayserver.docker.http.h2;

import baykit.bayserver.BayServer;
import baykit.bayserver.util.KeyVal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * HPACK spec
 * 
 * https://datatracker.ietf.org/doc/html/rfc7541
 */
public class HeaderTable {

    public static final String PSEUDO_HEADER_AUTHORITY = ":authority";
    public static final String PSEUDO_HEADER_METHOD = ":method";
    public static final String PSEUDO_HEADER_PATH = ":path";
    public static final String PSEUDO_HEADER_SCHEME = ":scheme";
    public static final String PSEUDO_HEADER_STATUS = ":status";

    static HeaderTable staticTable = new HeaderTable();
    static int staticSize;

    ArrayList<KeyVal> idxMap = new ArrayList<>();
    int addCount;
    Map<String, ArrayList<Integer>> nameMap = new HashMap<>();


    public KeyVal get(int idx) {
        if(idx <= 0 || idx > staticSize + idxMap.size())
            throw new IllegalArgumentException("idx=" + idx + " static=" + staticSize + " dynamic=" + idxMap.size());

        KeyVal kv;
        if(idx <= staticSize)
            kv = staticTable.idxMap.get(idx - 1);
        else 
            kv = idxMap.get((idx - staticSize) - 1);
        //BayServer.info("Header table get(" + idx + ")->(" + kv.name + "," + kv.value + ")");
        return kv;
    }
    
    public ArrayList<Integer> get(String name) {
        ArrayList<Integer> dynamicList = nameMap.get(name);
        ArrayList<Integer> staticList = staticTable.nameMap.get(name);

        ArrayList<Integer> idxList = new ArrayList<>();
        if(staticList != null)
            idxList.addAll(staticList);
        if(dynamicList != null) {
            for(int idx: dynamicList) {
                int realIndex = addCount - idx + staticSize;
                idxList.add(realIndex);
            }
        }
        //BayServer.info("Header table get(" + name + ")->" + idxList);
        return idxList;
    }
    
    public void insert(String name, String value) {
        idxMap.add(0, new KeyVal(name, value));
        addCount++;
        addToNameMap(name, addCount);
        //BayServer.info("Header table insert(" + name + "," + value + ") addCount=" + addCount);
    }

    public void setSize(int size) {
    }
    
    private HeaderTable() {
        
    }
    
    private void put(int idx, String name, String value) {
        if(idx != idxMap.size() + 1)
            throw new IllegalStateException();
        idxMap.add(new KeyVal(name, value));
        addToNameMap(name, idx);
    }

    private void addToNameMap(String name, int idx) {
        ArrayList<Integer> idxList = nameMap.get(name);
        if(idxList == null) {
            idxList = new ArrayList<>();
            nameMap.put(name, idxList);
        }
        idxList.add(idx);
    }
    
    public static HeaderTable createDynamicTable() { 
        HeaderTable t = new HeaderTable();
        return t;
    }

    static {
        staticTable.put(1, PSEUDO_HEADER_AUTHORITY, null);
        staticTable.put(2, PSEUDO_HEADER_METHOD, "GET");
        staticTable.put(3, PSEUDO_HEADER_METHOD, "POST");
        staticTable.put(4, PSEUDO_HEADER_PATH, "/");
        staticTable.put(5, PSEUDO_HEADER_PATH, "/index.html");
        staticTable.put(6, PSEUDO_HEADER_SCHEME, "http");
        staticTable.put(7, PSEUDO_HEADER_SCHEME, "https");
        staticTable.put(8, PSEUDO_HEADER_STATUS, "200");
        staticTable.put(9, PSEUDO_HEADER_STATUS, "204");
        staticTable.put(10, PSEUDO_HEADER_STATUS, "206");
        staticTable.put(11, PSEUDO_HEADER_STATUS, "304");
        staticTable.put(12, PSEUDO_HEADER_STATUS, "400");
        staticTable.put(13, PSEUDO_HEADER_STATUS, "404");
        staticTable.put(14, PSEUDO_HEADER_STATUS, "500");
        staticTable.put(15, "accept-charset", null);
        staticTable.put(16, "accept-encoding", "gzip, deflate");
        staticTable.put(17, "accept-language", null);
        staticTable.put(18, "accept-ranges", null);
        staticTable.put(19, "accept", null);
        staticTable.put(20, "access-control-allow-origin", null);
        staticTable.put(21, "age", null);
        staticTable.put(22, "allow", null);
        staticTable.put(23, "authorization", null);
        staticTable.put(24, "cache-control", null);
        staticTable.put(25, "content-disposition", null);
        staticTable.put(26, "content-encoding", null);
        staticTable.put(27, "content-language", null);
        staticTable.put(28, "content-length", null);
        staticTable.put(29, "content-location", null);
        staticTable.put(30, "content-range", null);
        staticTable.put(31, "content-type", null);
        staticTable.put(32, "cookie", null);
        staticTable.put(33, "date", null);
        staticTable.put(34, "etag", null);
        staticTable.put(35, "expect", null);
        staticTable.put(36, "expires", null);
        staticTable.put(37, "from", null);
        staticTable.put(38, "host", null);
        staticTable.put(39, "if-match", null);
        staticTable.put(40, "if-modified-since", null);
        staticTable.put(41, "if-none-match", null);
        staticTable.put(42, "if-range", null);
        staticTable.put(43, "if-unmodified-since", null);
        staticTable.put(44, "last-modified", null);
        staticTable.put(45, "link", null);
        staticTable.put(46, "location", null);
        staticTable.put(47, "max-forwards", null);
        staticTable.put(48, "proxy-authenticate", null);
        staticTable.put(49, "proxy-authorization", null);
        staticTable.put(50, "range", null);
        staticTable.put(51, "referer", null);
        staticTable.put(52, "refresh", null);
        staticTable.put(53, "retry-after", null);
        staticTable.put(54, "server", null);
        staticTable.put(55, "set-cookie", null);
        staticTable.put(56, "strict-transport-security", null);
        staticTable.put(57, "transfer-encoding", null);
        staticTable.put(58, "user-agent", null);
        staticTable.put(59, "vary", null);
        staticTable.put(60, "via", null);
        staticTable.put(61, "www-authenticate", null);
        
        staticSize = staticTable.idxMap.size();
    }

}
