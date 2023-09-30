package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.agent.transporter.Transporter;

public interface Secure {

    void setAppProtocols(String[] protocols);

    void reloadCert() throws Exception;

    Transporter createTransporter();
}
