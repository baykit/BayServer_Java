package baykit.bayserver.docker;

import baykit.bayserver.agent.transporter.Transporter;

public interface Secure {

    void setAppProtocols(String[] protocols);

    void reloadCert() throws Exception;

    Transporter createTransporter();
}
