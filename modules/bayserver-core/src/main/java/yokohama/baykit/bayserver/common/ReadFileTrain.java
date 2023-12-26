package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;

import java.io.*;
import java.nio.ByteBuffer;

public class ReadFileTrain extends Train {

    ReadOnlyShip ship;
    boolean available;

    public ReadFileTrain(ReadOnlyShip ship, Tour tur) throws FileNotFoundException {
        super(tur);
        this.ship = ship;
        this.available = true;

        tur.res.setConsumeListener((len, resume) -> {
            if(resume)
                available = true;
        });

    }

    @Override
    public void depart() throws HttpException {

        byte[] buf = new byte[tour.ship.protocolHandler.maxReqPacketDataSize()];
        try {
            while (true) {
                int c = ship.input.read(buf);
                if (c == -1)
                    break;

                ship.notifyRead(ByteBuffer.wrap(buf, 0, c));
                while (!available) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        BayLog.error(e);
                        break;
                    }
                }
            }
            ship.notifyEof();
        }
        catch(IOException e) {
            ship.notifyError(e);
        }
        finally {
            try {
                ship.input.close();
            }
            catch(IOException e) {
                BayLog.error(e);
            }
            ship.notifyClose();
        }
    }
}
