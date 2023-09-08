package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayException;
import yokohama.baykit.bayserver.BayLog;
import baykit.bayserver.docker.servlet.duck.*;
import yokohama.baykit.bayserver.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import yokohama.baykit.bayserver.docker.servlet.duck.FilterRegistrationDuck;
import yokohama.baykit.bayserver.docker.servlet.duck.ServletRegistrationDuck;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

public class WebXml {

    ServletDocker docker;

    public WebXml(ServletDocker docker) {
        this.docker = docker;
    }

    /**
     * Get child element list of element
     * @param parent
     * @param tag
     * @return
     */
    private ArrayList<Element> getChildElements(Element parent, String tag) {
        ArrayList<Element> elms = new ArrayList<>();
        Node n = parent.getFirstChild();
        while(n != null) {
            if(n instanceof Element && ((Element)n).getTagName().equals(tag)) {
                Element tagElm = (Element)n;
                elms.add(tagElm);
            }
            n = n.getNextSibling();
        }
        return elms;
    }

    /**
     * Get first child element of element
     * @param parent
     * @param tag
     * @return
     */
    private Element getFirstChildElement(Element parent, String tag) {
        Node n = parent.getFirstChild();
        while(n != null) {
            if(n instanceof Element && ((Element)n).getTagName().equals(tag)) {
                return (Element)n;
            }
            n = n.getNextSibling();
        }
        return null;
    }

    private String getChildElementText(Element parent, String tag) {
        Element child = getFirstChildElement(parent, tag);
        if(child == null)
            return null;
        
        return getChildText(child);
    }

    private long getChildElementLong(Element parent, String tag) {
        String  t = getChildElementText(parent, tag);
        if(StringUtil.empty(t)) {
            BayLog.warn("Invalid int value: " + t + " tag=" + tag);
        }
        else {
            try {
                return Long.parseLong(t);
            }
            catch(NumberFormatException e) {
                BayLog.warn(e + ": " + t +" tag=" + tag);
            }
        }
        return -1;
    }

    private String getChildText(Element elm) {
        return elm.getTextContent().trim();
    }

    /**
     * Parse web.xml
     * @param f
     * @throws BayException
     */
    void parseXml(File f) throws Exception {

        DocumentBuilder dbr = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        XmlHandler h = new XmlHandler();
        dbr.setErrorHandler(h);
        dbr.setEntityResolver(h);
        Document doc = dbr.parse(f);
        Element rt = doc.getDocumentElement();
        Node n = rt.getFirstChild();
        while (n != null) {
            if (n instanceof Element) {
                Element elm = (Element) n;
                String tag = elm.getTagName();
                switch (tag) {
                    case "display-name":
                        BayLog.info("Web Application Title: " + getChildText(elm));
                        break;

                    case "description":
                        BayLog.info("Web Application Description: " + getChildText(elm));
                        break;

                    case "context-param": {
                        String name = getChildElementText(elm, "param-name");
                        String value = getChildElementText(elm, "param-value");
                        if (!StringUtil.empty(name)) {
                            docker.ctxParams.put(name, value == null ? "" : value);
                        }
                        break;
                    }

                    case "filter": {
                        String name = getChildElementText(elm, "filter-name");
                        String clsName = getChildElementText(elm, "filter-class");
                        String async = getChildElementText(elm, "async-supported");
                        if (!StringUtil.empty(name) && !StringUtil.empty(clsName)) {
                            FilterRegistrationDuck reg = docker.ctx.addFilterDuck(name, clsName);
                            reg.setAsyncSupported(Boolean.getBoolean(async));
                            ArrayList<Element> params = getChildElements(elm, "init-param");
                            for (Element p : params) {
                                String pname = getChildElementText(p, "param-name");
                                String pvalue = getChildElementText(p, "param-value");
                                if (!StringUtil.empty(name)) {
                                    reg.setInitParameter(pname, pvalue == null ? "" : pvalue);
                                }
                            }
                        }
                        break;
                    }

                    case "filter-mapping": {
                        String name = getChildElementText(elm, "filter-name");
                        String pattern = getChildElementText(elm, "url-pattern");
                        if (!StringUtil.empty(name) && !StringUtil.empty(pattern)) {
                            if (docker.ctx.getFilterRegistrationDuck(name) == null) {
                                BayLog.warn(ServletMessage.get(ServletSymbol.SVT_FILTER_NOT_FOUND, name));
                            } else {
                                docker.filterStore.addMapping(name, pattern);
                            }
                        }
                        break;
                    }

                    case "listener": {
                        String cls = getChildElementText(elm, "listener-class");
                        if (cls != null) {
                            docker.listenerStore.addListener(cls, null);
                        }
                        break;
                    }

                    case "servlet": {
                        ServletRegistrationDuck reg = null;
                        String name = null;
                        Node svn = elm.getFirstChild();
                        while (svn != null) {
                            if (svn instanceof Element) {
                                Element sve = (Element) svn;
                                switch (sve.getTagName()) {
                                    case "servlet-name":
                                        name = getChildText(sve);
                                        break;

                                    case "servlet-class": {
                                        String clsName = getChildText(sve);
                                        if (StringUtil.isSet(name) && StringUtil.isSet(clsName)) {
                                            reg = docker.ctx.addServletDuck(name, clsName);
                                        }
                                        break;
                                    }

                                    case "async-supported":
                                        String async = getChildText(sve);
                                        if (reg != null) {
                                            reg.setAsyncSupported(Boolean.getBoolean(async));
                                        }
                                        break;

                                    case "multipart-config": {
                                        if (reg != null) {
                                            String loc = getChildElementText(sve, "location");
                                            long maxFs = getChildElementLong(sve, "max-file-size");
                                            long maxRs = getChildElementLong(sve, "max-request-size");
                                            long fsTh = getChildElementLong(sve, "file-size-threshold");
                                            if (StringUtil.isSet(loc) && maxRs >= 0 && maxRs >= 0 && fsTh >= 0) {
                                                reg.setMultipartConfigObject((int) fsTh, maxFs, maxRs, loc);
                                            }
                                        }
                                        break;
                                    }

                                    case "init-param": {
                                        if (reg != null) {
                                            String pname = getChildElementText(sve, "param-name");
                                            String pvalue = getChildElementText(sve, "param-value");
                                            if (StringUtil.isSet(name)) {
                                                reg.setInitParameter(pname, pvalue == null ? "" : pvalue);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                            svn = svn.getNextSibling();
                        }
                        break;
                    }

                    case "servlet-mapping": {
                        String name = getChildElementText(elm, "servlet-name");
                        String pattern = getChildElementText(elm, "url-pattern");
                        if (!StringUtil.empty(name) && !StringUtil.empty(pattern)) {
                            if (docker.servletStore.getRegistration(name) == null) {
                                BayLog.warn(ServletMessage.get(ServletSymbol.SVT_SERVLET_NOT_FOUND, name));
                            } else {
                                docker.servletStore.addMapping(name, pattern);
                            }
                        }
                        break;
                    }

                    case "welcome-file-list": {
                        for (Element wfile : getChildElements(elm, "welcome-file")) {
                            String name = wfile.getTextContent().trim();
                            if (!StringUtil.empty(name))
                                docker.welcomefiles.add(name);
                        }
                        break;
                    }

                    case "resource-ref": {
                        String name = getChildElementText(elm, "res-ref-name");
                        String type = getChildElementText(elm, "res-type");
                        String auth = getChildElementText(elm, "res-auth");
                        docker.resorceRefStore.addResourceRef(name, type, auth);
                        break;
                    }

                    case "error-page": {
                        String code = getChildElementText(elm, "error-code");
                        String type = getChildElementText(elm, "exception-type");
                        String loc = getChildElementText(elm, "location");
                        if (StringUtil.isSet(code) && StringUtil.isSet(loc)) {
                            try {
                                docker.errorPageStore.add(Integer.parseInt(code), loc);
                            } catch (NumberFormatException e) {
                                BayLog.error(e);
                            }
                        }
                        else if(StringUtil.isSet(type) && StringUtil.isSet(loc)) {
                            try {
                                docker.errorPageStore.add(docker.ctx.getClassLoader().loadClass(type), loc);
                            } catch (Exception e) {
                                BayLog.error(e);
                            }
                        }
                        else {
                            BayLog.error(f + ": Cannot detect error page");
                        }
                        break;
                    }

                    case "session-config": {
                        String to = getChildElementText(elm, "timeout");
                        if (StringUtil.isSet(to)) {
                            try {
                                docker.sessionStore.sessionLifeTime = Integer.parseInt(to);
                            } catch (NumberFormatException e) {
                                BayLog.error(e);
                            }
                        }
                        break;
                    }

                    default:
                        BayLog.warn(f + ": Unkonwn tag: " + tag);
                        break;
                }
            }
            n = n.getNextSibling();
        }
    }
}
