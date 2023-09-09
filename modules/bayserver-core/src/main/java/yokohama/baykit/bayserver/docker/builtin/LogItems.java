package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.tour.Tour;

import java.text.SimpleDateFormat;
import java.util.Date;

abstract class LogItems {


    /*************************************************/
    /* Implemented classes                           */
    /*************************************************/

    static class TextItem extends LogItem {

        /** text to print */
        String text;

        TextItem(String text) {
            this.text = text;
        }

        String getItem(Tour tour) {
            return text;
        }
    }

    /**
     * Return null result
     */
    static class NullItem extends LogItem {
        static LogItemFactory factory = NullItem::new;

        String getItem(Tour tour) {
            return null;
        }
    }

    /**
     * Return remote IP address (%a)
     */
    static class RemoteIpItem extends LogItem {
        static LogItemFactory factory = RemoteIpItem::new;

        String getItem(Tour tour) {
            return tour.req.remoteAddress;
        }
    }

    /**
     * Return local IP address (%A)
     */
    static class ServerIpItem extends LogItem {
        static LogItemFactory factory = ServerIpItem::new;

        String getItem(Tour tour) {
            return tour.req.serverAddress;
        }
    }

    /**
     * Return number of bytes that is sent from clients (Except HTTP headers)
     * (%B)
     */
    static class RequestBytesItem1 extends LogItem {
        static LogItemFactory factory = RequestBytesItem1::new;

        String getItem(Tour tour) {
            int bytes = tour.req.headers.contentLength();
            if (bytes < 0)
                bytes = 0;
            return String.valueOf(bytes);
        }
    }

    /**
     * Return number of bytes that is sent from clients in CLF format (Except
     * HTTP headers) (%b)
     */
    static class RequestBytesItem2 extends LogItem {
        static LogItemFactory factory = RequestBytesItem2::new;

        String getItem(Tour tour) {
            int bytes = tour.req.headers.contentLength();
            if (bytes <= 0)
                return "-";
            else
                return String.valueOf(bytes);
        }
    }

    /**
     * Return connection status (%c)
     */
    static class ConnectionStatusItem extends LogItem {
        static LogItemFactory factory = ConnectionStatusItem::new;

        String getItem(Tour tour) {
            if (tour.isAborted())
                return "X";
            else
                return "-";
        }
    }

    /**
     * Return file name (%f)
     */
    static class FileNameItem extends LogItem {
        static LogItemFactory factory = FileNameItem::new;

        String getItem(Tour tour) {
            return tour.req.scriptName;
        }
    }

    /**
     * Return remote host name (%H)
     */
    static class RemoteHostItem extends LogItem {
        static LogItemFactory factory = RemoteHostItem::new;

        String getItem(Tour tour) {
            return tour.req.remoteHost();
        }
    }

    /**
     * Return remote log name (%l)
     */
    static class RemoteLogItem extends LogItem {
        static LogItemFactory factory = RemoteLogItem::new;

        String getItem(Tour tour) {
            return null;
        }
    }

    /**
     * Return request protocol (%m)
     */
    static class ProtocolItem extends LogItem {
        static LogItemFactory factory = ProtocolItem::new;

        String getItem(Tour tour) {
            return tour.req.protocol;
        }
    }

    /**
     * Return requested header (%{Foobar}i)
     */
    static class RequestHeaderItem extends LogItem {
        static LogItemFactory factory = RequestHeaderItem::new;

        /** Header name */
        String name;

        public void init(String param) {
            if (param == null)
                param = "";
            this.name = param;
        }

        String getItem(Tour tour) {
            return tour.req.headers.get(name);
        }
    }

    /**
     * Return request method (%m)
     */
    static class MethodItem extends LogItem {
        static LogItemFactory factory = MethodItem::new;

        String getItem(Tour tour) {
            return tour.req.method;
        }
    }

    /**
     * Return responde header (%{Foobar}o)
     */
    static class ResponseHeaderItem extends LogItem {
        static LogItemFactory factory = ResponseHeaderItem::new;

        /** Header name */
        String name;

        public void init(String param) {
            if (param == null)
                param = "";
            this.name = param;
        }

        String getItem(Tour tour) {
            return tour.res.headers.get(name);
        }
    }

    /**
     * The server port (%p)
     */
    static class PortItem extends LogItem {
        static LogItemFactory factory = PortItem::new;

        String getItem(Tour tour) {
            return String.valueOf(tour.req.serverPort);
        }
    }

    /**
     * Return query string (%q)
     */
    static class QueryStringItem extends LogItem {
        static LogItemFactory factory = QueryStringItem::new;

        String getItem(Tour tour) {
            String qStr = tour.req.queryString;
            if (qStr != null)
                return '?' + qStr;
            else
                return null;
        }
    }

    /**
     * The start line (%r)
     */
    static class StartLineItem extends LogItem {
        static LogItemFactory factory = StartLineItem::new;

        String getItem(Tour tour) {
            return tour.req.method+ " " + tour.req.uri + " " + tour.req.protocol;
        }
    }

    /**
     * Return status (%s)
     */
    static class StatusItem extends LogItem {
        static LogItemFactory factory = StatusItem::new;

        String getItem(Tour tour) {
            return String.valueOf(tour.res.headers.status());
        }
    }

    /**
     * Return current time (%{format}t)
     */
    static class TimeItem extends LogItem {
        static LogItemFactory factory = TimeItem::new;

        /** Formatter */
        SimpleDateFormat formatter = new SimpleDateFormat(
                "[dd/MMM/yyyy:HH:mm:ss Z]");

        public void init(String param) {
            if (param != null)
                formatter = new SimpleDateFormat(param);
        }

        String getItem(Tour tour) {
            return formatter.format(new Date());
        }
    }

    /**
     * Return how long request took (%T)
     */
    static class IntervalItem extends LogItem {
        static LogItemFactory factory = IntervalItem::new;

        String getItem(Tour tour) {
            return String.valueOf(tour.interval / 1000);
        }
    }

    /**
     * Return remote user (%u)
     */
    static class RemoteUserItem extends LogItem {
        static LogItemFactory factory = RemoteUserItem::new;

        String getItem(Tour tour) {
            return tour.req.remoteUser;
        }
    }

    /**
     * Return requested URL(not content query string) (%U)
     */
    static class RequestUrlItem extends LogItem {
        static LogItemFactory factory = RequestUrlItem::new;

        String getItem(Tour tour) {
            String url = tour.req.uri== null ? "" : tour.req.uri;
            int pos = url.indexOf('?');
            if (pos != -1)
                url = url.substring(0, pos);
            return url;
        }
    }

    /**
     * Return the server name (%v)
     */
    static class ServerNameItem extends LogItem {
        static LogItemFactory factory = ServerNameItem::new;

        String getItem(Tour tour) {
            return tour.req.serverName;
        }
    }


}
