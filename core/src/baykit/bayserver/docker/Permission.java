package baykit.bayserver.docker;

import baykit.bayserver.HttpException;
import baykit.bayserver.tour.Tour;

import java.nio.channels.SocketChannel;

public interface Permission {

    void socketAdmitted(SocketChannel ch) throws HttpException;

    void tourAdmitted(Tour tour) throws HttpException;
}
