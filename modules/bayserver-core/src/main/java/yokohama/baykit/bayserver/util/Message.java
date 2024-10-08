package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.bcf.BcfDocument;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.BcfParser;
import yokohama.baykit.bayserver.bcf.ParseException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;

/**
 * Message class
 */
public class Message {
    
    public HashMap<String, String> messages = new HashMap<>();
    
    public void init(String path, Locale locale) throws ParseException {
        String lang = locale.getLanguage();
        init(path + ".bcf");
        if(StringUtil.isSet(lang) && !lang.equals("en"))
            init(path + "_" + lang + ".bcf");
    }

    private void init(String path) throws ParseException {
        if(Message.class.getResource(path) == null) {
            BayLog.warn("Cannot find message file: %s", path);
            return;
        }
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parseResource(path);
        //if(BayServer.logLevel == BayServer.LOG_LEVEL_DEBUG)
        //    doc.print();

        for(Object o: doc.contentList) {
            if(o instanceof BcfKeyVal) {
                BcfKeyVal kv = (BcfKeyVal)o;
                messages.put(kv.key, kv.value);
            }
        }
    }

    public String getMessage(Enum key, Object... args) {
        return getMessage(key.toString(), args);
    }

    public String getMessage(String key, Object... args) {
        String msg = messages.get(key);
        if(msg == null)
            msg = key;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            pw.printf(msg, args);
        }
        catch(Exception e) {
            BayLog.error(e);
            return msg;
        }
        pw.flush();
        return sw.toString();
    }
}