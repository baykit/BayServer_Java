package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.HttpException;

import java.io.IOException;

public interface ReqContentHandler {

    void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException;

    void onEndReqContent(Tour tur) throws IOException, HttpException;

    boolean onAbortReq(Tour tur);

    ReqContentHandler devNull = new ReqContentHandler() {
        @Override
        public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
            tur.req.consumed(Tour.TOUR_ID_NOCHECK, len, lis);
        }

        @Override
        public void onEndReqContent(Tour tur) throws IOException, HttpException {

        }

        @Override
        public boolean onAbortReq(Tour tur) {
            return false;
        }
    };
}
