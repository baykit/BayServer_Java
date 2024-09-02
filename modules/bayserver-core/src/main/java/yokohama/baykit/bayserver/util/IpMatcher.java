package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.Symbol;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpMatcher {

    boolean matchAll;
    BigInteger ipMaskInt;
    BigInteger mask;

    public IpMatcher(String ipDesc) throws UnknownHostException {
        if (ipDesc.equals("*"))
            matchAll = true;
        else
            parseCidr(ipDesc);
    }

    public boolean match(InetAddress adr) {
        if (matchAll)
            return true;

        BigInteger adrInt = new BigInteger(1, adr.getAddress());

        return adrInt.and(mask).equals(ipMaskInt);
    }

    private void parseCidr(String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        if (parts.length != 2)
            throw new IllegalArgumentException(
                    BayMessage.get(Symbol.CFG_INVALID_IP_DESC, cidr));

        InetAddress ip = InetAddress.getByName(parts[0]);
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        }
        catch(NumberFormatException e) {
            throw new IllegalArgumentException(
                    BayMessage.get(Symbol.CFG_INVALID_IP_DESC, cidr));
        }

        mask = getMask(ip, prefixLength);
        ipMaskInt = new BigInteger(1, ip.getAddress()).and(mask);
    }

    private static BigInteger getMask(InetAddress inetAddress, int prefixLength) {
        if (inetAddress.getAddress().length == 4) { // IPv4
            return BigInteger.valueOf(-1).shiftLeft(32 - prefixLength);
        } else { // IPv6
            return BigInteger.valueOf(-1).shiftLeft(128 - prefixLength);
        }
    }
}
