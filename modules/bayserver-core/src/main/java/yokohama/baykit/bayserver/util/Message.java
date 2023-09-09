package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.bcf.BcfDocument;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.bcf.BcfParser;
import yokohama.baykit.bayserver.bcf.ParseException;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;

/**
 * Message class
 */
public class Message {
    
    public HashMap<String, String> messages = new HashMap<>();
    
    public void init(String conf, Locale locale) throws ParseException {
        String lang = locale.getLanguage();
        init(conf + ".bcf");
        if(StringUtil.isSet(lang) && !lang.equals("en"))
            init(conf + "_" + lang + ".bcf");
    }

    private void init(String file) throws ParseException {
        if(!new File(file).exists()) {
            BayLog.warn("Cannot find message send_file: %s", file);
            return;
        }
        BcfParser p = new BcfParser();
        BcfDocument doc = p.parse(file);
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
            pw.print(msg);
        }
        pw.flush();
        return sw.toString();
    }
}