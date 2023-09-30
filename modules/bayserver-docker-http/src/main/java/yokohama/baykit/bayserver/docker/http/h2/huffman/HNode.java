package yokohama.baykit.bayserver.docker.http.h2.huffman;

public class HNode {
    int value = -1;   //  if vlaue > 0 leaf node else inter node
    public HNode one;
    public HNode zero;
}
