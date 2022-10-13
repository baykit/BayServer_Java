import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class DemoContextListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(ServletContextEvent ev) {
        ev.getServletContext().log("contextInitialized called: " + this);
    }

    @Override
    public void contextDestroyed(ServletContextEvent ev) {
        ev.getServletContext().log("contextDestroyed called: " + this);
    }
}
