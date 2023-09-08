package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.Symbol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

public class IpMatcher {

    boolean matchAll;
    byte[] netAdr = new byte[4];
    byte[] maskAdr;

    public IpMatcher(String ipDesc) throws UnknownHostException {
        if (ipDesc.equals("*"))
            matchAll = true;
        else
            parseIp(ipDesc);
    }

    public boolean match(InetAddress adr) {
        if (matchAll)
            return true;

        byte[] remoteAdr = adr.getAddress();
        if (remoteAdr.length != maskAdr.length)
            return false;  // IPv4 and IPv6 don't match each other

        for (int i = 0; i < remoteAdr.length; i++) {
            if ((byte) (remoteAdr[i] & maskAdr[i]) != netAdr[i])
                return false;
        }
        return true;
    }

    private void parseIp(String ipDesc) throws UnknownHostException {
        StringTokenizer st = new StringTokenizer(ipDesc, "/");
        String ip, mask;
        if (!st.hasMoreTokens())
            throw new IllegalArgumentException(
                    BayMessage.get(Symbol.CFG_INVALID_IP_DESC, ipDesc));

        ip = st.nextToken();
        if (!st.hasMoreTokens())
            mask = "255.255.255.255";
        else
            mask = st.nextToken();

        byte[] ipAdr = getIpAddr(ip);
        maskAdr = getIpAddr(mask);
        if (ipAdr.length != maskAdr.length) {
            throw new IllegalArgumentException(
                    BayMessage.get(Symbol.CFG_IPV4_AND_IPV6_ARE_MIXED, ipDesc));
        }
        netAdr = new byte[maskAdr.length];
        for (int i = 0; i < maskAdr.length; i++) {
            netAdr[i] = (byte) (ipAdr[i] & maskAdr[i]);
        }
    }

    private byte[] getIpAddr(String ip) throws UnknownHostException {
        byte[] ipAdr = InetAddress.getByName(ip).getAddress();
        return ipAdr;
    }
}
