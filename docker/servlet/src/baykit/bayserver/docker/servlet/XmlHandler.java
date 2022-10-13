package baykit.bayserver.docker.servlet;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import org.xml.sax.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class XmlHandler implements ErrorHandler, EntityResolver {
    
    @Override
    public void warning(SAXParseException e) throws SAXException {
        BayLog.warn("%s [line %d]", e.toString(), e.getLineNumber());
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        BayLog.error(e, "%s [line %d]", e.toString(), e.getLineNumber());
        throw e;
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        BayLog.error(e, "%s [line %d]", e.toString(), e.getLineNumber());
        throw e;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        File file = null;
        switch (publicId) {
            case "-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN":
                file = new File(BayServer.bservHome, "lib/dtd/web_22.dtd");
                break;
            case "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN":
                file = new File(BayServer.bservHome, "lib/dtd/web_23.dtd");
                break;
        }
        if(file == null)
            return null;
        else
            return new InputSource(new FileInputStream(file));
    }
}