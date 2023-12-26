package yokohama.baykit.bayserver.common.docker;

import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Club;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.util.ClassUtil;
import yokohama.baykit.bayserver.util.StringUtil;

public abstract class ClubBase extends DockerBase implements Club {
    
    String fileName;
    String extension;
    String charset;
    boolean decodePathInfo = true;

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        int p = elm.arg.lastIndexOf('.');
        if (p == -1) {
            fileName = elm.arg;
            extension = null;
        } else {
            fileName = elm.arg.substring(0, p);
            extension = elm.arg.substring(p + 1);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "decodepathinfo":
                decodePathInfo = StringUtil.parseBool(kv.value);
                break;
            case "charset":
                String cs = kv.value;
                if(StringUtil.isSet(cs))
                    charset = cs;
                break;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements Club
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public String extension() {
        return extension;
    }

    @Override
    public String charset() {
        return charset;
    }

    @Override
    public boolean decodePathInfo() {
        return decodePathInfo;
    }

    @Override
    public boolean matches(String fname) {
        // check club
        int pos = fname.indexOf(".");
        if(pos == -1) {
            // fname has no extension
            if(extension != null)
                return false;

            if(fileName.equals("*"))
                return true;

            return fname.equals(fileName);
        }
        else {
            //fname has extension
            if(extension == null)
                return false;

            String nm = fname.substring(0, pos);
            String ext = fname.substring(pos + 1);

            if(!extension.equals("*") && !ext.equals(extension))
                return false;

            if(fileName.equals("*"))
                return true;
            else
                return nm.equals(fileName);
        }
    }

    public String toString() {
        return ClassUtil.getLocalName(getClass());
    }
}
