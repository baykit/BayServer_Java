import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ProxyServlet extends HttpServlet {

    private String url;

    public void init(ServletConfig cfg) throws ServletException {

        super.init(cfg);

        url = cfg.getInitParameter("url");
        if (url == null || url.equals(""))
            throw new ServletException("URL parameter not found");

        if (!url.endsWith("/"))
            url += "/";
    }

    public void service(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

        // Get relative path
        String rpath = req.getPathInfo();

        if (rpath == null)
            rpath = "";

        if (rpath.startsWith("/"))
            rpath = rpath.substring(1);

        if (req.getQueryString() != null)
            rpath += "?" + req.getQueryString();

        // Create forward URL
        URL forwardUrl = new URL(new URL(url), rpath);
        log("forward: " + forwardUrl);
        HttpURLConnection con = (HttpURLConnection) forwardUrl.openConnection();

        con.setInstanceFollowRedirects(false);

        InputStream in = req.getInputStream();
        OutputStream out = res.getOutputStream();

        // Set request method
        con.setRequestMethod(req.getMethod());

        // Set request headers
        Enumeration names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String value = req.getHeader(name);

            if (name.equalsIgnoreCase("connection"))
                continue;

            con.setRequestProperty(name, value);
        }
        con.setRequestProperty("connection", "close");

        OutputStream appOut = null;
        InputStream appIn = null;

        try {
            byte[] buf = new byte[1024];

            if (!"GET".equalsIgnoreCase(req.getMethod())
                    && !"HEAD".equalsIgnoreCase(req.getMethod())) {
                con.setDoOutput(true);
                appOut = con.getOutputStream();
                while (true) {
                    int c = in.read(buf);
                    if (c == -1)
                        break;
                    appOut.write(buf, 0, c);
                }
            }

            // Get response status
            res.setStatus(con.getResponseCode());

            // Get response headers
            Map headers = con.getHeaderFields();
            Iterator it = headers.keySet().iterator();
            while (it.hasNext()) {
                String key = (String) it.next();

                // If key == null, it is status line header
                if (key == null)
                    continue;
                List values = (List) headers.get(key);
                Iterator vit = values.iterator();
                while (vit.hasNext()) {
                    String value = (String) vit.next();
                    res.setHeader(key, value);
                }
            }

            try {
                appIn = con.getInputStream();
            } catch (IOException e) {
                appIn = con.getErrorStream();
            }

            while (true) {
                int c = appIn.read(buf);
                if (c == -1)
                    break;
                out.write(buf, 0, c);
            }
        }
        catch(IOException e) {
            e.printStackTrace();
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        finally {
            try {
                if (appOut != null)
                    appOut.close();
                if (appIn != null)
                    appIn.close();
            } catch (IOException e) {
            }
        }
    }

}