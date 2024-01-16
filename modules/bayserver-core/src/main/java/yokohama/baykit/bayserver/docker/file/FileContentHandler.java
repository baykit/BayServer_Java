package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.MultiplexingValve;
import yokohama.baykit.bayserver.agent.transporter.PlainTransporter;
import yokohama.baykit.bayserver.agent.transporter.SimpleDataListener;
import yokohama.baykit.bayserver.agent.transporter.SpinReadTransporter;
import yokohama.baykit.bayserver.common.ReadChannelTaxi;
import yokohama.baykit.bayserver.common.ReadChannelTrain;
import yokohama.baykit.bayserver.taxi.TaxiRunner;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;
import yokohama.baykit.bayserver.train.TrainRunner;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;

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
            Channel ch = Channels.newChannel(in);
            int bufsize = tur.ship.protocolHandler.maxResPacketDataSize();

            switch(BayServer.harbor.fileSendMethod()) {
                case Spin: {
                    GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                    int timeout = 10;
                    SpinReadTransporter tp = new SpinReadTransporter(bufsize);
                    sendFileShip.init(ch, tur, tp);
                    tp.init(
                            agt.spinHandler,
                            new SimpleDataListener(sendFileShip),
                            new FileInputStream(file),
                            (int)file.length(),
                            timeout,
                            null);

                    int sid = sendFileShip.id();
                    tur.res.setConsumeListener((len, resume) -> {
                        if(resume) {
                            sendFileShip.resumeRead(sid);
                        }
                    });
                    tp.openReadValve();
                    break;
                }

                case Taxi:{
                    ReadChannelTaxi txi = new ReadChannelTaxi(tur.ship.agentId);
                    sendFileShip.init(ch, tur, txi);
                    PlainTransporter tp = new PlainTransporter(false, bufsize, false);
                    GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                    tp.init(ch, new SimpleDataListener(sendFileShip), new MultiplexingValve(agt.multiplexer, ch));
                    txi.setChannelListener(ch, tp);

                    int sid = sendFileShip.id();
                    tur.res.setConsumeListener((len, resume) -> {
                        if(resume) {
                            sendFileShip.resumeRead(sid);
                        }
                    });
                    if(!TaxiRunner.post(tur.ship.agentId, txi)) {
                        throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Taxi is busy!");
                    }
                    break;
                }

                case Train:
                    sendFileShip.init(ch, tur, null);
                    PlainTransporter tp = new PlainTransporter(false, bufsize, false);
                    GrandAgent agt = GrandAgent.get(tur.ship.agentId);
                    tp.init(ch, new SimpleDataListener(sendFileShip), new MultiplexingValve(agt.multiplexer, ch));

                    Train tr = new ReadChannelTrain(ch, tp, tur);
                    if(!TrainRunner.post(tr)) {
                        throw new HttpException(HttpStatus.SERVICE_UNAVAILABLE, "Train is busy");
                    }
                    break;

                default:
                    throw new Sink();
            }

        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, file.getPath());
        }
    }


}
