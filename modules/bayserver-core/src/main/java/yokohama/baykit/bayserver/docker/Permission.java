package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;

import java.nio.channels.SocketChannel;

public interface Permission {

    void socketAdmitted(SocketChannel ch) throws HttpException;

    void tourAdmitted(Tour tour) throws HttpException;
}
