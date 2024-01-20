package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.RudderState;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

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
    public void onReadContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s endContent", tur);
        sendFileAsync(tur, path, tur.res.charset());
        abortable = false;
    }

    @Override
    public boolean onAbort(Tour tur) {
        BayLog.debug("%s onAbort aborted=%s", tur, abortable);
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

        SendFileShip sendFileShip = new SendFileShip();

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

            InputStream in = new FileInputStream(file);
            ReadableByteChannel ch = Channels.newChannel(in);
            Rudder rd = new ChannelRudder(ch);
            int bufsize = tur.ship.protocolHandler.maxResPacketDataSize();

            switch(BayServer.harbor.fileSendMethod()) {
                case Spin: {
                    GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                    sendFileShip.init(rd, agt.multiplexer, tur);
                    agt.spinMultiplexer.addState(rd, new RudderState(rd, new SimpleDataListener(sendFileShip)));

                    int sid = sendFileShip.id();
                    tur.res.setConsumeListener((len, resume) -> {
                        if(resume) {
                            sendFileShip.resumeRead(sid);
                        }
                    });
                    agt.spinMultiplexer.reqRead(rd);
                    break;
                }

                case Taxi:{
                    GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                    sendFileShip.init(rd, agt.taxiMultiplexer, tur);
                    agt.taxiMultiplexer.addState(rd, new RudderState(rd, new SimpleDataListener(sendFileShip)));

                    int sid = sendFileShip.id();
                    tur.res.setConsumeListener((len, resume) -> {
                        if(resume) {
                            sendFileShip.resumeRead(sid);
                        }
                    });

                    agt.taxiMultiplexer.reqRead(rd);
                    break;
                }

                default:
                    throw new Sink();
            }

        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, file.getPath());
        }
    }


}
