package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.Constants;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.docker.Town;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdEndContent;
import yokohama.baykit.bayserver.docker.http.h1.command.CmdHeader;
import yokohama.baykit.bayserver.common.WarpData;
import yokohama.baykit.bayserver.common.WarpHandler;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.common.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.protocol.PacketStore;
import yokohama.baykit.bayserver.protocol.ProtocolException;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.protocol.ProtocolHandlerFactory;

import java.io.IOException;

import static yokohama.baykit.bayserver.docker.http.h1.H1WarpHandler.CommandState.*;

public class H1WarpHandler extends H1ProtocolHandler implements WarpHandler {

    public static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<H1Command, H1Packet, H1Type> {

        @Override
        public ProtocolHandler<H1Command, H1Packet, H1Type> createProtocolHandler(
                PacketStore<H1Packet, H1Type> pktStore) {
            return new H1WarpHandler(pktStore);
        }
    }

    enum CommandState {
        ReadHeader,
        ReadContent,
        Finished,
    }

    public static final int FIXED_WARP_ID = 1;
    CommandState state;

    public H1WarpHandler(PacketStore<H1Packet, H1Type> pktStore) {
        super(pktStore, false);
        resetState();
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        super.reset();
        resetState();
    }

    /////////////////////////////////////
    // Implements H1CommandHandler
    /////////////////////////////////////

    @Override
    public NextSocketAction handleHeader(CmdHeader cmd) throws IOException {
        WarpShip wsip = ship();
        Tour tur = wsip.getTour(FIXED_WARP_ID);
        WarpData wdat = WarpData.get(tur);
        BayLog.debug("%s handleHeader status=%d", wdat, cmd.status);
        wsip.keeping = false;
        if (state == Finished)
            changeState(ReadHeader);

        if (state != ReadHeader)
            throw new ProtocolException("Header command not expected");

        if(BayServer.harbor.traceHeader()) {
            BayLog.info("%s warp_http: resStatus: %d", wdat, cmd.status);
        }

        cmd.headers.forEach(
                nv -> {
                    tur.res.headers.add(nv[0], nv[1]);
                    if(BayServer.harbor.traceHeader()) {
                        BayLog.info("%s warp_http: resHeader: %s=%s", wdat, nv[0], nv[1]);
                    }
                });

        tur.res.headers.setStatus(cmd.status);
        int resContLen = tur.res.headers.contentLength();
        tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
        //BayLog.debug(wdat + " contLen in header=" + resContLen);
        if (resContLen == 0 || cmd.status == HttpStatus.NOT_MODIFIED) {
            endResContent(tur);
        } else {
            changeState(ReadContent);
            int sid = wsip.id();
            tur.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    wsip.resume(sid);
                }
            });
        }
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleContent(CmdContent cmd) throws IOException {
        Tour tur = ship().getTour(FIXED_WARP_ID);
        WarpData wdat = WarpData.get(tur);
        BayLog.debug("%s handleContent len=%d posted=%d contLen=%d", wdat, cmd.len, tur.res.bytesPosted, tur.res.bytesLimit);

        if (state != ReadContent)
            throw new ProtocolException("Content command not expected");


        boolean available = tur.res.sendContent(Tour.TOUR_ID_NOCHECK, cmd.buffer, cmd.start, cmd.len);
        if (tur.res.bytesPosted == tur.res.bytesLimit) {
            endResContent(tur);
            return NextSocketAction.Continue;
        }
        else if(!available) {
            return NextSocketAction.Suspend;
        }
        else {
            return NextSocketAction.Continue;
        }
    }

    @Override
    public NextSocketAction handleEndContent(CmdEndContent cmdEndContent) {
        throw new Sink();
    }

    @Override
    public boolean reqFinished() {
        return state == Finished;
    }

    /////////////////////////////////////
    // Implements WarpHandler
    /////////////////////////////////////
    @Override
    public int nextWarpId() {
        return H1WarpHandler.FIXED_WARP_ID;
    }


    @Override
    public WarpData newWarpData(int warpId){
        return new WarpData(ship(), warpId);
    }

    @Override
    public void postWarpHeaders(Tour tur) throws IOException {
        Town town = tur.town;

        //BayServer.debug(this + " construct header");
        String townPath = town.name();
        if (!townPath.endsWith("/"))
            townPath += "/";

        WarpShip sip = ship();
        String newUri = sip.docker().warpBase() + tur.req.uri.substring(townPath.length());

        CmdHeader cmd =
                CmdHeader.newReqHeader(
                        tur.req.method,
                        newUri,
                        Constants.HTTP_11);

        tur.req.headers.headerNames().forEach(
                name -> {
                    tur.req.headers.headerValues(name).forEach(
                            value -> cmd.addHeader(name, value));
                });

        if(tur.req.headers.contains(Headers.X_FORWARDED_FOR))
            cmd.setHeader(Headers.X_FORWARDED_FOR, tur.req.headers.get(Headers.X_FORWARDED_FOR));
        else
            cmd.setHeader(Headers.X_FORWARDED_FOR, tur.req.remoteAddress);

        if(tur.req.headers.contains(Headers.X_FORWARDED_PROTO))
            cmd.setHeader(Headers.X_FORWARDED_PROTO, tur.req.headers.get(Headers.X_FORWARDED_PROTO));
        else
            cmd.setHeader(Headers.X_FORWARDED_PROTO, tur.isSecure ? "https" : "http");

        if(tur.req.headers.contains(Headers.X_FORWARDED_PORT))
            cmd.setHeader(Headers.X_FORWARDED_PORT, tur.req.headers.get(Headers.X_FORWARDED_PORT));
        else
            cmd.setHeader(Headers.X_FORWARDED_PORT, Integer.toString(tur.req.serverPort));

        if(tur.req.headers.contains(Headers.X_FORWARDED_HOST))
            cmd.setHeader(Headers.X_FORWARDED_HOST, tur.req.headers.get(Headers.X_FORWARDED_HOST));
        else
            cmd.setHeader(Headers.X_FORWARDED_HOST, tur.req.headers.get(Headers.HOST));

        cmd.setHeader(Headers.HOST, sip.docker().host() + ":" + sip.docker().port());
        cmd.setHeader(Headers.CONNECTION, "Keep-Alive");

        if(BayServer.harbor.traceHeader()) {
            cmd.headers.forEach(kv -> BayLog.info("%s warp_http reqHdr: %s=%s", tur, kv[0], kv[1]));
        }

        sip.post(cmd);

    }

    @Override
    public void postWarpContents(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        CmdContent cmd = new CmdContent(buf, start, len);
        ship().post(cmd, lis);
    }

    @Override
    public void postWarpEnd(Tour tur) throws IOException {
        CmdEndContent cmd = new CmdEndContent();
        ship().post(cmd, () -> {
            ship.agent.nonBlockingHandler.askToRead(ship.ch);
        });
    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
        if(protocol != null && protocol.equalsIgnoreCase("h2")) {
        }
    }

    /////////////////////////////////////
    // Implements ProtocolHandler
    /////////////////////////////////////

    @Override
    public boolean onProtocolError(ProtocolException e) throws IOException {
        throw new Sink();
    }

    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////

    void resetState() {
        changeState(Finished);
    }

    public String toString() {
        return ship().toString();
    }

    private void endResContent(Tour tur) throws IOException {
        ship().endWarpTour(tur);
        tur.res.endContent(Tour.TOUR_ID_NOCHECK);
        resetState();
        ship().keeping = true;
    }

    private void changeState(CommandState newState) {
        state = newState;
    }

    WarpShip ship() {
        return (WarpShip) ship;
    }
}
