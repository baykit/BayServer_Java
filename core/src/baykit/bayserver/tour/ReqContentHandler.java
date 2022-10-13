package baykit.bayserver.tour;

import baykit.bayserver.HttpException;

import java.io.IOException;

public interface ReqContentHandler {

    void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException;

    void onEndContent(Tour tur) throws IOException, HttpException;

    boolean onAbort(Tour tur);

    ReqContentHandler devNull = new ReqContentHandler() {
        @Override
        public void onReadContent(Tour tur, byte[] buf, int start, int len) throws IOException {

        }

        @Override
        public void onEndContent(Tour tur) throws IOException, HttpException {

        }

        @Override
        public boolean onAbort(Tour tur) {
            return false;
        }
    };
}
