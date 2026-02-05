package yokohama.baykit.bayserver.util;

public class ByteArrayUtil {

    public static int indexOf(byte[] array, int from, int to, byte target) {
        for (int k = from; k < to; k++)
            if (array[k] == target)
                return k;
        return -1;
    }
}
