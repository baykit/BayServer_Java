package baykit.bayserver.docker.servlet.duck;

import baykit.bayserver.docker.servlet.ReqInfo;
import baykit.bayserver.docker.servlet.ServletDocker;


import java.io.IOException;

public class RequestDispatcherDuck {

    /** Servlet instance */
    protected final Object servlet;
    
    private final ServletDocker docker;
    
    private final ServletHelper helper;
    
    protected final ReqInfo orgInfo;

    protected final String ctxPath, reqUri, queryString, pathInfo, servletPath;

    public RequestDispatcherDuck(
            Object servlet,
            ServletDocker docker, ReqInfo orginfo,
            String ctxPath,
            String reqUri,
            String servletPath,
            String pathInfo,
            String queryString) {
        
        this.servlet = servlet;
        this.docker = docker;
        this.orgInfo = orginfo;
        this.reqUri = reqUri;
        this.queryString = queryString;
        this.pathInfo = pathInfo;
        this.servletPath = servletPath;
        this.ctxPath = ctxPath;
        this.helper = docker.servletHelper;
    }


    public void forwardDuck(Object req, Object res)
            throws ServletExceptionDuck, IOException {

        if(docker.resHelper.isCommitted(res))
            throw new IllegalStateException("Response is committed");

        Object newReq = docker.duckFactory.newForwardRequest(req, reqUri, pathInfo, servletPath,  queryString);
        helper.service(servlet, newReq, res);
    }

    // including request
    public void includeDuck(Object req, Object res)
            throws ServletExceptionDuck, IOException {

        String oldRequestUri = (String) docker.reqHelper.getAttribute(req, docker.ATTR_REQUEST_URI);
        String oldContextPath = (String) docker.reqHelper.getAttribute(req, docker.ATTR_CONTEXT_PATH);
        String oldServletPath = (String) docker.reqHelper.getAttribute(req, docker.ATTR_SERVLET_PATH);
        String oldPathInfo = (String) docker.reqHelper.getAttribute(req, docker.ATTR_PATH_INFO);
        String oldQueryString = (String) docker.reqHelper.getAttribute(req, docker.ATTR_QUERY_STRING);

        docker.reqHelper.setAttribute(req, docker.ATTR_REQUEST_URI, reqUri);
        docker.reqHelper.setAttribute(req, docker.ATTR_CONTEXT_PATH, ctxPath);
        docker.reqHelper.setAttribute(req, docker.ATTR_SERVLET_PATH, servletPath);
        docker.reqHelper.setAttribute(req, docker.ATTR_PATH_INFO, pathInfo);
        docker.reqHelper.setAttribute(req, docker.ATTR_QUERY_STRING, queryString);

        try {
            Object newReq = docker.duckFactory.newIncluderequest(req, queryString);
            helper.service(servlet, newReq, res);
        } finally {

            if (oldRequestUri != null) {
                docker.reqHelper.setAttribute(req, docker.ATTR_REQUEST_URI, oldRequestUri);
                docker.reqHelper.setAttribute(req, docker.ATTR_CONTEXT_PATH, oldContextPath);
                docker.reqHelper.setAttribute(req, docker.ATTR_SERVLET_PATH, oldServletPath);
                docker.reqHelper.setAttribute(req, docker.ATTR_PATH_INFO, oldPathInfo);
                docker.reqHelper.setAttribute(req, docker.ATTR_QUERY_STRING, oldQueryString);
            } else {
                docker.reqHelper.removeAttribute(req, docker.ATTR_REQUEST_URI);
                docker.reqHelper.removeAttribute(req, docker.ATTR_CONTEXT_PATH);
                docker.reqHelper.removeAttribute(req, docker.ATTR_SERVLET_PATH);
                docker.reqHelper.removeAttribute(req, docker.ATTR_PATH_INFO);
                docker.reqHelper.removeAttribute(req, docker.ATTR_QUERY_STRING);
            }
        }
    }


}
