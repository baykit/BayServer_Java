package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.agent.multiplexer.RudderState;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileContentHandler implements ReqContentHandler {

    final File path;
    boolean abortable;

    public FileContentHandler(File path) {
        this.path = path;
        this.abortable = true;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s file:onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s file:endContent", tur);
        sendFileAsync(tur, path, tur.res.charset());
        abortable = false;
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s file:onAbort aborted=%s", tur, abortable);
        return abortable;
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Sending file methods
    ////////////////////////////////////////////////////////////////////////////////
    public void sendFileAsync(Tour tur, File file, String charset) throws HttpException {
        if (file.isDirectory()) {
            throw new HttpException(HttpStatus.FORBIDDEN, file.getPath());
        } else if (!file.exists()) {
            throw new HttpException(HttpStatus.NOT_FOUND, file.getPath());
        }

        String mimeType = null;

        String rname = file.getName();
        int pos = rname.lastIndexOf('.');
        if (pos >= 0) {
            String ext = rname.substring(pos + 1).toLowerCase();
            mimeType = Mimes.getType(ext);
        }

        if (mimeType == null)
            mimeType = "application/octet-stream";

        if (mimeType.startsWith("text/") && charset != null)
            mimeType = mimeType + "; charset=" + charset;

        //resHeaders.setStatus(HttpStatus.OK);
        tur.res.headers.setContentType(mimeType);
        tur.res.headers.setContentLength(file.length());
        try {
            tur.res.sendHeaders(Tour.TOUR_ID_NOCHECK);

            int bufsize = tur.ship.protocolHandler.maxResPacketDataSize();
            GrandAgent agt = GrandAgent.get(tur.ship.agentId);
            Multiplexer mpx = null;
            Rudder rd = null;

            switch(BayServer.harbor.fileMultiplexer()) {
                case Spin: {
                    AsynchronousFileChannel ch =
                            AsynchronousFileChannel.open(Paths.get(file.getPath()), StandardOpenOption.READ);
                    rd = new AsynchronousFileChannelRudder(ch);
                    mpx = agt.spinMultiplexer;

                    break;
                }

                case Job:{
                    InputStream in = new FileInputStream(file);
                    ReadableByteChannel ch = Channels.newChannel(in);
                    rd = new ReadableByteChannelRudder(ch);
                    mpx = agt.jobMultiplexer;

                    break;
                }

                case Taxi:{
                    InputStream in = new FileInputStream(file);
                    ReadableByteChannel ch = Channels.newChannel(in);
                    rd = new ReadableByteChannelRudder(ch);
                    mpx = agt.taxiMultiplexer;

                    break;
                }

                case Pigeon: {
                    AsynchronousFileChannel ch =
                            AsynchronousFileChannel.open(Paths.get(file.getPath()), StandardOpenOption.READ);
                    rd = new AsynchronousFileChannelRudder(ch);
                    mpx = agt.pegionMultiplexer;

                    break;
                }

                default:
                    throw new Sink();
            }

            SendFileShip sendFileShip = new SendFileShip();
            PlainTransporter tp = new PlainTransporter(
                    mpx,
                    sendFileShip,
                    true,
                    8192,
                    false);

            sendFileShip.init(rd, tp, tur);
            int sid = sendFileShip.id();
            tur.res.setConsumeListener((len, resume) -> {
                if(resume) {
                    sendFileShip.resumeRead(sid);
                }
            });

            mpx.addRudderState(rd, new RudderState(rd, tp));
            mpx.reqRead(rd);


        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, file.getPath());
        }
    }


}
