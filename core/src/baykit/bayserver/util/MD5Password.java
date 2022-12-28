package baykit.bayserver.util;

import baykit.bayserver.BayLog;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Password {

    public static String encode(String password) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] dat = password.getBytes();
			md.update(dat);
			return bytesToString(md.digest());
		} catch (NoSuchAlgorithmException e) {
			BayLog.fatal(e);
			return null;
		}
	}

	public static String bytesToString(byte[] bytes) {
		StringBuffer ret = new StringBuffer("");
		for (byte b : bytes) {
			int val = b | 0xFFFFFF00;
			ret.append(Integer.toHexString(val).substring(6));
		}
		return ret.toString();
	}
}
