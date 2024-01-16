package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.bcf.*;
import yokohama.baykit.bayserver.util.MD5Password;

import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

public class Groups {

    static class Member {
        String name;
        String digest;

        public Member(String name, String digest) {
            this.name = name;
            this.digest = digest;
        }

        public boolean validate(String password) {
            if(password == null)
                return false;
            String dig = MD5Password.encode(password);
            return digest.equals(dig);
        }
    }
    
    public static class Group {
        String name;
        HashSet<String> members = new HashSet<>();

        public Group(String name) {
            this.name = name;
        }
        
        public void add(String mem) {
            members.add(mem);
        }
        
        public boolean validate(String mName, String pass) {
            if(!members.contains(mName))
                return false;
            Member m = allMembers.get(mName);
            if(m == null)
                return false;
            return m.validate(pass);
        }
    }

    public static HashMap<String, Group> allGroups = new HashMap<>();
    public static HashMap<String, Member> allMembers = new HashMap<>();

    public static void init(String conf) throws ParseException {
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(conf);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();
        for(BcfObject o : doc.contentList) {
            if(o instanceof BcfElement) {
                BcfElement elm = (BcfElement)o;
                if(elm.name.equalsIgnoreCase("group")) {
                    initGroups(elm);
                }
                else if(elm.name.equalsIgnoreCase("member")) {
                    initMembers(elm);
                }
            }
        }
    }

    public static Group getGroup(String name) {
        return allGroups.get(name);
    }
    
    //////////////////////////////////////////////////////////////////////////
    // private methods
    //////////////////////////////////////////////////////////////////////////
    private static void initGroups(BcfElement elm) {
        for(Object o: elm.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                Group g = new Group(kv.key);
                allGroups.put(g.name, g);
                StringTokenizer st = new StringTokenizer(kv.value);
                while(st.hasMoreTokens()) {
                    String mName = st.nextToken();
                    g.add(mName);
                }
            }
        }
    }

    private static void initMembers(BcfElement elm) {
        for(Object o: elm.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                Member m = new Member(kv.key, kv.value);
                allMembers.put(m.name, m);
            }
        }
    }
}
