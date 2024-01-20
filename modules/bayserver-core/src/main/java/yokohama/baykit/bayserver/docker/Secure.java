package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.agent.multiplexer.TransporterBase;

public interface Secure {

    void setAppProtocols(String[] protocols);

    void reloadCert() throws Exception;

    TransporterBase createTransporter();
}
