package baykit.bayserver.docker.ajp;

import baykit.bayserver.protocol.Command;

import java.io.IOException;

/**
 * AJP Protocol
 * https://tomcat.apache.org/connectors-doc/ajp/ajpv13a.html
 */
public abstract class AjpCommand extends Command<AjpCommand, AjpPacket, AjpType, AjpCommandHandler> {

    public boolean toServer;

    public AjpCommand(AjpType type, boolean toServer) {
        super(type);
        this.toServer = toServer;
    }

    public void unpack(AjpPacket pkt) throws IOException {
        if(pkt.type() != type)
            throw new IllegalArgumentException();
        this.toServer = pkt.toServer;
    }

    /**
     * super class method must be called from last line of override method since header cannot be packed before data is constructed
     */
    public void pack(AjpPacket pkt) throws IOException {
        if(pkt.type() != type)
            throw new IllegalArgumentException();
        pkt.toServer = toServer;
        packHeader(pkt);
    }

    void packHeader(AjpPacket pkt) throws IOException {

        AjpPacket.AjpAccessor acc = pkt.newAjpHeaderAccessor();
       if(pkt.toServer) {
            acc.putByte(0x12);
            acc.putByte(0x34);
        }
        else {
            acc.putByte('A');
            acc.putByte('B');
        }
        acc.putByte((pkt.dataLen() >> 8) & 0xff);
        acc.putByte(pkt.dataLen() & 0xff);
    }


}



