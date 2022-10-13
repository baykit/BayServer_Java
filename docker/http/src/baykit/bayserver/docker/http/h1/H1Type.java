package baykit.bayserver.docker.http.h1;

public enum H1Type {

    Header(1),
    Content(2),
    EndContent(3);  // Dummy command

    static H1Type[] types = {
            Header,
            Content,
            EndContent
    };

    public int no;
    H1Type(int no) {
        this.no = no;
    }

    public static H1Type getType(int no) {
        if(no <= 0 || no > types.length)
            throw new ArrayIndexOutOfBoundsException("Invalid H1 type: " + no);
        return types[no - 1];
    }
}
