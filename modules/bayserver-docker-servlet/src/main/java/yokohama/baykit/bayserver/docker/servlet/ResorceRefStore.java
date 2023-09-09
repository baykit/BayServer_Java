package yokohama.baykit.bayserver.docker.servlet;

import java.util.ArrayList;

public class ResorceRefStore {

    public static class ResourceRefDesc {
        public String name;
        public String type;
        public String auth;

        public ResourceRefDesc(String name, String type, String auth) {
            this.name = name;
            this.type = type;
            this.auth = auth;
        }
    }

    ArrayList<ResourceRefDesc> resources = new ArrayList<>();

    public void addResourceRef(String name, String type, String auth) {
        resources.add(new ResourceRefDesc(name, type, auth));
    }

}
