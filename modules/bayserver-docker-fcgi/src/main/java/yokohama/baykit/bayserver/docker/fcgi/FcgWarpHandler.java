package yokohama.baykit.bayserver.docker.fcgi;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.WarpData;
import yokohama.baykit.bayserver.common.WarpHandler;
import yokohama.baykit.bayserver.common.WarpShip;
import yokohama.baykit.bayserver.docker.fcgi.command.*;
import yokohama.baykit.bayserver.protocol.*;
import yokohama.baykit.bayserver.ship.Ship;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.CGIUtil;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Headers;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.StringTokenizer;

import static yokohama.baykit.bayserver.docker.fcgi.FcgWarpHandler.CommandState.ReadContent;
import static yokohama.baykit.bayserver.docker.fcgi.FcgWarpHandler.CommandState.ReadHeader;

public class FcgWarpHandler implements WarpHandler, FcgHandler {

    static class WarpProtocolHandlerFactory implements ProtocolHandlerFactory<FcgCommand, FcgPacket, FcgType> {

        @Override
        public ProtocolHandler<FcgCommand, FcgPacket, FcgType> createProtocolHandler(
                PacketStore<FcgPacket, FcgType> pktStore) {
            FcgWarpHandler warpHandler = new FcgWarpHandler();
            FcgCommandUnPacker commandUnpacker = new FcgCommandUnPacker(warpHandler);
            FcgPacketUnPacker packetUnpacker = new FcgPacketUnPacker(commandUnpacker, pktStore);
            PacketPacker packetPacker = new PacketPacker<>();
            CommandPacker commandPacker = new CommandPacker<>(packetPacker, pktStore);
            FcgProtocolHandler protocolHandler =
                    new FcgProtocolHandler(
                            warpHandler,
                            packetUnpacker,
                            packetPacker,
                            commandUnpacker,
                            commandPacker,
                            false);
            warpHandler.init(protocolHandler);
            return protocolHandler;
        }
    }

    int curWarpId;

    enum CommandState {
        ReadHeader,
        ReadContent,
    }

    FcgProtocolHandler protocolHandler;
    CommandState state;
    CharArrayWriter lineBuf = new CharArrayWriter();

    // for read header/contents
    int pos;
    int last;
    byte[] data;

    public FcgWarpHandler() {
        resetState();
    }

    private void init(FcgProtocolHandler protocolHandler) {
        this.protocolHandler = protocolHandler;
    }




    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////
    @Override
    public void reset() {
        resetState();
        lineBuf.reset();
        pos = 0;
        last = 0;
        data = null;
        curWarpId++;
    }

    /////////////////////////////////////
    // Implements WarpHandler
    /////////////////////////////////////
    @Override
    public synchronized int nextWarpId() {
        return ++curWarpId;
    }

    @Override
    public WarpData newWarpData(int warpId){
        return new WarpData(ship(), warpId);
    }

    @Override
    public void sendHeaders(Tour tur) throws IOException {
        sendBeginReq(tur);
        sendParams(tur);
    }

    @Override
    public void sendContent(Tour tur, byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        sendStdIn(tur, buf, start, len, lis);
    }

    @Override
    public void sendEnd(Tour tur, boolean keepAlive, DataConsumeListener lis) throws IOException {
        sendStdIn(tur, null, 0, 0, lis);
    }

    @Override
    public void verifyProtocol(String protocol) throws IOException {
    }

    /////////////////////////////////////
    // Implement FcgCommandHandler
    /////////////////////////////////////
    @Override
    public NextSocketAction handleBeginRequest(CmdBeginRequest cmd) throws IOException {
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleEndRequest(CmdEndRequest cmd) throws IOException {
        Tour tur = ship().getTour(cmd.reqId);
        endReqContent(tur);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleParams(CmdParams cmd) throws IOException {
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleStdErr(CmdStdErr cmd) throws IOException {
        String msg = new String(cmd.data, cmd.start, cmd.length);
        BayLog.error(this + " server error:" + msg);
        return NextSocketAction.Continue;
    }

    @Override
    public NextSocketAction handleStdIn(CmdStdIn cmd) throws IOException {
        throw new ProtocolException("Invalid FCGI command: " + cmd.type);
    }

    @Override
    public NextSocketAction handleStdOut(CmdStdOut cmd) throws IOException {
        Tour tur = ship().getTour(cmd.reqId);
        if(tur == null)
            throw new Sink("Tour not found");

        if (cmd.length == 0) {
            // stdout end
            resetState();
            return NextSocketAction.Continue;
        }

        data = cmd.data;
        pos = cmd.start;
        last = cmd.start + cmd.length;

        if (state == ReadHeader)
            readHeader(tur);

        if (pos < last) {
            if (state == ReadContent) {
                boolean available = tur.res.sendResContent(Tour.TOUR_ID_NOCHECK, data, pos, last - pos);
                if(!available)
                    return NextSocketAction.Suspend;
            }
        }

        return NextSocketAction.Continue;
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
    private void readHeader(Tour tur) throws IOException {
        WarpData wdat = WarpData.get(tur);

        boolean headerFinished = parseHeader(wdat.resHeaders);
        if (headerFinished) {

            wdat.resHeaders.copyTo(tur.res.headers);

            // Check HTTP Status from headers
            String status = wdat.resHeaders.get(Headers.STATUS);
            if (!StringUtil.empty(status)) {
                StringTokenizer st = new StringTokenizer(status);
                try {
                    String code = st.nextToken();
                    int stCode = Integer.parseInt(code);
                    tur.res.headers.setStatus(stCode);
                    tur.res.headers.remove(Headers.STATUS);
                } catch (Exception e) {
                    BayLog.error(e);
                    throw new ProtocolException("warp: Status header of server is invalid: " + status);
                }
            }

            Ship sip = ship();

            BayLog.debug(sip + " fcgi: read header status=" + status + " contlen=" + wdat.resHeaders.contentLength());
            int sid = sip.id();
            tur.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    sip.resumeRead(sid);
                }
            });

            tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
            changeState(ReadContent);
        }
    }

    private boolean parseHeader(Headers headers) throws IOException {

        while (true) {
            if (pos == last) {
                // no byte data
                break;
            }

            int c = data[pos++];

            if (c == '\r')
                continue;
            else if (c == '\n') {
                String line = lineBuf.toString();
                if (line.length() == 0)
                    return true;
                int colonPos = line.indexOf(':');
                if (colonPos < 0)
                    throw new ProtocolException("fcgi: Header line of server is invalid: " + line);
                else {
                    String name = null, value = null;
                    int p;
                    for (p = colonPos - 1; p >= 0; p--) {
                        // trimming header name
                        if (!Character.isWhitespace(line.charAt(p))) {
                            name = line.substring(0, p + 1);
                            break;
                        }
                    }
                    for (p = colonPos + 1; p < line.length(); p++) {
                        // trimming header value
                        if (!Character.isWhitespace(line.charAt(p))) {
                            value = line.substring(p);
                            break;
                        }
                    }
                    if (name == null || value == null)
                        throw new ProtocolException("fcgi: Header line of server is invalid: " + line);
                    headers.add(name, value);
                    if (BayServer.harbor.traceHeader())
                        BayLog.info("%s fcgi_warp: resHeader: %s=%s", ship(), name, value);
                }
                lineBuf.reset();
            } else {
                lineBuf.write(c);
            }
        }
        return false;
    }

    private void endReqContent(Tour tur) throws IOException {
        ship().endWarpTour(tur);
        tur.res.endResContent(Tour.TOUR_ID_NOCHECK);
        resetState();
    }

    private void changeState(CommandState newState) {
        state = newState;
    }

    void resetState() {
        changeState(CommandState.ReadHeader);
    }



    private void sendStdIn(Tour tur, byte[] data, int ofs, int len, DataConsumeListener lis) throws IOException {
        CmdStdIn cmd = new CmdStdIn(WarpData.get(tur).warpId, data, ofs, len);
        ship().post(cmd, lis);
    }

    private void sendBeginReq(Tour tur) throws IOException {
        CmdBeginRequest cmd = new CmdBeginRequest(WarpData.get(tur).warpId);
        cmd.role = CmdBeginRequest.FCGI_RESPONDER;
        cmd.keepConn = true;
        ship().post(cmd);
    }


    private void sendParams(Tour tur) throws IOException {
        String scriptBase =  ((FcgWarpDocker) ship().docker()).scriptBase;
        if(scriptBase == null)
            scriptBase = tur.town.location();

        if(StringUtil.empty(scriptBase)) {
            throw new IOException(tur.town + " scriptBase of fcgi docker or location of town is not specified.");
        }

        String docRoot = ((FcgWarpDocker) ship().docker()).docRoot;
        if(docRoot == null)
            docRoot = tur.town.location();

        if(StringUtil.empty(docRoot)) {
            throw new IOException(tur.town + " docRoot of fcgi docker or location of town is not specified.");
        }

        int warpId = WarpData.get(tur).warpId;
        final CmdParams cmd = new CmdParams(warpId);

        final String scriptFname[] = new String[1];
        CGIUtil.getEnv(tur.town.name(), docRoot, scriptBase, tur, (name, value) -> {
            if(name.equals(CGIUtil.SCRIPT_FILENAME))
                scriptFname[0] = value;
            else
                cmd.addParam(name, value);
        });

        scriptFname[0] = "proxy:fcgi://" +  ((FcgWarpDocker) ship().docker()).host + ":" +  ship().docker().port() + scriptFname[0];
        cmd.addParam(CGIUtil.SCRIPT_FILENAME, scriptFname[0]);

        cmd.addParam(FcgParams.CONTEXT_PREFIX, "");
        cmd.addParam(FcgParams.UNIQUE_ID, Long.toString(System.currentTimeMillis()));
        //cmd.addParam(FcgParams.X_FORWARDED_FOR, tour.remoteAddress);
        //cmd.addParam(FcgParams.X_FORRARDED_PROTO, tour.isSecure ? "https" : "http");
        //cmd.addParam(FcgParams.X_FORWARDED_PORT, Integer.toString(tour.serverPort));

        if(BayServer.harbor.traceHeader()) {
            cmd.params.forEach( kv ->
                    BayLog.info("%s fcgi_warp: env: %s=%s", ship(), kv[0], kv[1]));
        }

        ship().post(cmd);

        CmdParams cmdParamsEnd = new CmdParams(warpId);
        ship().post(cmdParamsEnd);
    }

    WarpShip ship() {
        return (WarpShip) protocolHandler.ship;
    }
}
