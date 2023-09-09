package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.train.Train;
import yokohama.baykit.bayserver.util.HttpStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class SendFileTrain extends Train {

    Tour tour;
    int tourId;
    FileInputStream in;

    public SendFileTrain(Tour tur, File file) throws FileNotFoundException {
        super(tur);
        this.tour = tur;
        this.tourId = tur.id();
        this.in = new FileInputStream(file);

        tur.res.setConsumeListener((len, resume) -> {
        });

    }

    @Override
    public void depart() throws HttpException {

        byte[] buf = new byte[tour.ship.protocolHandler.maxReqPacketDataSize()];
        try {
            while (true) {
                int c;
                try {
                    c = in.read(buf);
                    if (c == -1)
                        break;
                } catch (IOException e) {
                    tour.res.sendError(tourId, HttpStatus.INTERNAL_SERVER_ERROR, null, e);
                    break;
                }
                tour.res.sendContent(tourId, buf, 0, c);
                while (!tour.res.available) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        break;
                    }
                }
            }
            tour.res.endContent(tourId);
        }
        catch(IOException e) {
            BayLog.error(e);
        }
        finally {
            try {
                in.close();
            }
            catch(IOException e) {
                BayLog.error(e);
            }
        }
    }
}
