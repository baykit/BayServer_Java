package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.SecureTransporter;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Secure;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.util.StringUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;

public class BuiltInSecureDocker extends DockerBase implements Secure {

    public static final boolean DEFAULT_CLIENT_AUTH = false;
    public static final String DEFAULT_SSL_PROTOCOL = "TLS";
    
    // SSL setting
    File keyStore;
    String keyStorePass;
    boolean clientAuth = DEFAULT_CLIENT_AUTH;
    String sslProtocol = DEFAULT_SSL_PROTOCOL;
    public File keyFile;
    public File certFile;
    File certs;
    String certsPass;
    boolean traceSSL;
    public SSLContext sslctx;
    String appProtocols[];

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        if(keyStore == null && (keyFile == null || certFile == null)) {
            throw new ConfigException(elm.fileName, elm.lineNo, "Key file or cert file is not specified");
        }

        try {
            initSSL();
        } catch (GeneralSecurityException | IOException e) {
            BayLog.error(e);
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    BayMessage.get(Symbol.CFG_SSL_INIT_ERROR, e.getMessage()),
                    e);
        }
    }

    //////////////////////////////////////////////////////
    // Implements DockerBase
    //////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        try {
            switch(kv.key.toLowerCase()) {
                default:
                    return false;

                case "key":
                    keyFile = getFilePath(kv.value);
                    break;

                case "cert":
                    certFile = getFilePath(kv.value);
                    break;

                case "keystore":
                    keyStore = getFilePath(kv.value);
                    break;

                case "keystorepass":
                    keyStorePass = kv.value;
                    break;

                case "clientauth":
                    clientAuth = Boolean.parseBoolean(kv.value);
                    break;

                case "sslprotocol":
                    sslProtocol = kv.value;
                    break;

                case "trustcerts":
                    certs = getFilePath(kv.value);
                    break;

                case "certspass":
                    certsPass = kv.value;
                    break;

                case "tracessl":
                    traceSSL = StringUtil.parseBool(kv.value);
                    break;
            }
            return true;
        }
        catch(FileNotFoundException e) {
            BayLog.error(e);
            throw new ConfigException(
                    kv.fileName,
                    kv.lineNo,
                    BayMessage.get(
                            Symbol.CFG_FILE_NOT_FOUND,
                            e.getMessage()));
        }
    }

    //////////////////////////////////////////////////////
    // Implements Secure
    //////////////////////////////////////////////////////

    @Override
    public void setAppProtocols(String[] protocols) {
        this.appProtocols = protocols;
    }

    @Override
    public PlainTransporter newTransporter(int agtId, Ship ship) {
        SecureTransporter tp = new SecureTransporter(
                GrandAgent.get(agtId).netMultiplexer,
                ship,
                true,
                -1,
                traceSSL,
                sslctx,
                appProtocols);
        tp.init();
        return tp;
    }

    @Override
    public void reloadCert() throws Exception {
        initSSL();
    }


    //////////////////////////////////////////////////////
    // Custom methods
    //////////////////////////////////////////////////////

    public void initSSL() throws GeneralSecurityException, IOException {
        if(traceSSL)
            BayLog.info("init SSL engine");

        sslctx = SSLContext.getInstance(sslProtocol);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        KeyStore ks = KeyStore.getInstance("JKS");
        //KeyStore ks = KeyStore.getInstance("PKCS12");

        char[] passphrase;
        if(keyStore == null) {
            passphrase = new char[0];
            ks.load(null, null);

            RSAPrivateKey privKey = loadKeyFile();
            Certificate[] chain = loadChain();

            ks.setKeyEntry("keychain", privKey, new char[0], chain);
        }
        else {
            passphrase = keyStorePass.toCharArray();
            FileInputStream keyStoreIn = new FileInputStream(keyStore);
            ks.load(keyStoreIn, passphrase);
            keyStoreIn.close();
        }


        ArrayList<TrustManager> trustManagerArray = new ArrayList<>();
        kmf.init(ks, passphrase);


        TrustManager[] trustManagers = null;
        if (certs != null) {

            // Get system default certs
            TrustManagerFactory systemTmf = TrustManagerFactory.getInstance("SunX509");
            systemTmf.init((KeyStore) null);

            FileInputStream certsIn = new FileInputStream(certs);
            KeyStore certsStore = KeyStore.getInstance("JKS");
            certsStore.load(certsIn, certsPass.toCharArray());

            TrustManagerFactory customTmf = TrustManagerFactory
                    .getInstance("SunX509");
            customTmf.init(certsStore);

            trustManagerArray.addAll(Arrays.asList(customTmf.getTrustManagers()));

            // Create custom trust managers
            trustManagers = trustManagerArray.toArray(new TrustManager[0]);
        }

        //sslctx.init(kmf.getKeyManagers(), trustManagers, null);
        sslctx.init(kmf.getKeyManagers(), null, null);
    }

    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////
    private File getFilePath(String fileName) throws FileNotFoundException {
        File file = new File(fileName);
        if (!file.isAbsolute())
            file = new File(new File(BayServer.bservHome), fileName);

        if (!file.isFile()) {
            throw new FileNotFoundException(fileName);
        }
        else
            return file;
    }

    private RSAPrivateKey loadKeyFile() throws IOException, GeneralSecurityException {
        StringBuilder s = new StringBuilder();
        BufferedReader r = new BufferedReader(new FileReader(keyFile));
        String line = r.readLine();
        boolean isRsa;
        switch(line) {
            case "-----BEGIN PRIVATE KEY-----":
                isRsa = false;
                break;

            case "-----BEGIN RSA PRIVATE KEY-----":
                isRsa = true;
                break;

            default:
                throw new SecurityException("This file does not seems to be a private key file: " + keyFile);
        }

       // if(isRsa)
       //     throw new SecurityException("RSA format is not supported on this VM: " + keyFile);

        while(true) {
            line = r.readLine();
            if(line == null)
                break;
            if(line.startsWith("----")) {
                continue;
            }
            s.append(line);
        }
        r.close();

        byte[] data = Base64.getDecoder().decode(s.toString());

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(data);
        RSAPrivateKey privKey = (RSAPrivateKey)kf.generatePrivate(privSpec);
        return privKey;

        //return data;
    }

    private Certificate[] loadChain() throws IOException, GeneralSecurityException {
        FileInputStream fis = new FileInputStream(certFile);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection c = cf.generateCertificates(fis);
        Certificate chain[] = (Certificate[]) c.toArray(new Certificate[0]);
        fis.close();
        return chain;
    }
}
