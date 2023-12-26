package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.common.DataConsumeListener;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressor {

    public interface CompressListener {
        void onCompressed(byte[] buf, int start, int len, DataConsumeListener lis) throws IOException;
    }

    class CallbackOutputStream extends OutputStream {

        DataConsumeListener lis;
        void setConsumeListener(DataConsumeListener lis) {
            this.lis = lis;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            listener.onCompressed(b, off, len, lis);
        }

        @Override
        public void write(int b) throws IOException {
            listener.onCompressed(new byte[]{(byte)b}, 0, 1, lis);
        }
    }

    CompressListener listener;
    GZIPOutputStream gout;
    CallbackOutputStream cbCout;

    public GzipCompressor(CompressListener lis) throws IOException {
        this.listener = lis;
        this.cbCout = new CallbackOutputStream();
        this.gout = new GZIPOutputStream(cbCout);
    }

    public void compress(byte[] buf, int start, int len, DataConsumeListener lis) throws IOException {
        cbCout.setConsumeListener(lis);
        gout.write(buf, start, len);
        gout.flush();
    }

    public void finish() throws IOException {
        gout.finish();
        gout.close();
    }
}
