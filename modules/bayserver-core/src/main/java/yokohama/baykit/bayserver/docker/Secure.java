package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.agent.multiplexer.Transporter;

public interface Secure {

    void setAppProtocols(String[] protocols);

    void reloadCert() throws Exception;

    Transporter createTransporter();
}
