package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.docker.servlet.duck.HttpSessionDuck;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class SessionStore {

    public static final int DEFAULT_SESSION_LIFETIME = 3600;
    public int sessionLifeTime = DEFAULT_SESSION_LIFETIME;
    long sesCounter;

    HashMap<String, HttpSessionDuck> sessions = new HashMap<>();

    ServletDocker docker;

    public SessionStore(ServletDocker docker) {
        this.docker = docker;
    }

    public HttpSessionDuck getSession(String sid) {
        synchronized (sessions) {
            if (sid != null) {
                HttpSessionDuck ses = sessions.get(sid);
                if (ses != null && ses.isValid()) {
                    long cur = System.currentTimeMillis();
                    if (cur > ses.getLastAccessedTime() + sessionLifeTime * 1000) {
                        ses.invalidate();
                    } else {
                        return ses;
                    }
                }
            }
        }
        return null;
    }

    public HttpSessionDuck createSession() {

        HttpSessionDuck ses = docker.duckFactory.newSession(newSessionId(), docker);
        synchronized (sessions) {
            sessions.put(ses.getId(), ses);
        }
        return ses;
    }

    public String newSessionId() {

        long sesNo;
        synchronized (this) {
            sesNo = sesCounter++;
        }

        String encoded = "";
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] result = md5.digest(Long.toHexString(sesNo).getBytes());
            for(byte b: result)
                encoded += Integer.toHexString((int)b & 0xff);
        } catch (NoSuchAlgorithmException e) {
            BayLog.fatal(e);
        }

        return encoded;
    }


    public void remove(HttpSessionDuck ses) {
        synchronized (sessions) {
            sessions.remove(ses.getId());
        }
    }


}
