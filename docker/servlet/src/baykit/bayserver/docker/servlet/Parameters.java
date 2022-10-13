package baykit.bayserver.docker.servlet;

import baykit.bayserver.BayLog;
import baykit.bayserver.util.KeyVal;
import baykit.bayserver.util.KeyValListParser;
import baykit.bayserver.util.URLDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class Parameters {

    public Map<String, String[]> paramMap;

    public Parameters() {
        paramMap = new HashMap<>();
    }

    public Parameters(Map<String, String[]> paramMap) {
        this.paramMap = paramMap;
    }

    /**
     * Parse query string
     */
    public void parse(String queryString, String charset, boolean prioritize) {

        if (queryString == null)
            return;

        KeyValListParser p = new KeyValListParser();
        ArrayList<KeyVal> paramList = p.parse(queryString);
        decode(paramList, charset, prioritize);
    }

    /**
     * Parse query string from strem
     */
    public void parse(InputStream stream, String charset, boolean prioritize) throws IOException {

        KeyValListParser p = new KeyValListParser();
        ArrayList<KeyVal> paramList = p.parse(stream);
        decode(paramList, charset, prioritize);
    }

    /**
     * Decode url-encoded parameters in decoded Parameters.
     */
    public void decode(
            ArrayList<KeyVal> parameters, 
            String charset,
            boolean prioritize) {

        for(KeyVal nv : parameters) {
            String name;
            try {
                name = URLDecoder.decode(nv.name, charset);
            } catch (UnsupportedEncodingException e) {
                BayLog.error(e);
                try {
                    name = URLDecoder.decode(nv.name, null);
                }
                catch(UnsupportedEncodingException ex) {
                    name = null;
                }
            }
            
            String val = null;
            try {
                val = URLDecoder.decode(nv.value, charset);
            } catch (UnsupportedEncodingException e) {
                BayLog.error(e);
                try {
                    val = URLDecoder.decode(nv.value, null);
                }
                catch(UnsupportedEncodingException ex) {
                    name = null;
                }
            }

            String[] thisVals = paramMap.get(name);
            String[] newVals;
            if (thisVals == null) {
                newVals = new String[] {val};
            }
            else {
                newVals = new String[thisVals.length + 1];
                if(prioritize) {
                    newVals[0] = val;
                    System.arraycopy(thisVals, 0, newVals, 1, thisVals.length);
                }
                else {
                    System.arraycopy(thisVals, 0, newVals, 0, thisVals.length);
                    newVals[newVals.length - 1] = val;
                }
            }
            paramMap.put(name, newVals);
        }
    }

    /**
     * Append parameters
     */
    public void append(Parameters ref, boolean prioritize) {

        for (String name : ref.paramMap.keySet()) {
            String[] refVals = ref.paramMap.get(name);

            String[] thisVals = this.paramMap.get(name);
            String[] newVals;
            if (thisVals != null) {
                newVals = new String[thisVals.length + refVals.length];
                if (prioritize) {
                    System.arraycopy(refVals, 0, newVals, 0, refVals.length);
                    System.arraycopy(thisVals, 0, newVals, refVals.length, thisVals.length);
                } else {
                    System.arraycopy(thisVals, 0, newVals, 0, thisVals.length);
                    System.arraycopy(refVals, 0, newVals, thisVals.length, refVals.length);
                }
                this.paramMap.put(name, newVals);
            } else {
                newVals = Arrays.copyOf(refVals, refVals.length);
            }
            this.paramMap.put(name, newVals);
        }
    }
    
}

