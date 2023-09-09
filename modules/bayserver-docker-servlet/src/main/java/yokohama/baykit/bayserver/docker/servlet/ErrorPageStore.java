package yokohama.baykit.bayserver.docker.servlet;

import java.util.HashMap;
import java.util.Map;

public class ErrorPageStore {

    public Map<Integer, String> codePageMap = new HashMap<>();

    public Map<Class<?>, String> classPageMap = new HashMap<>();

    public void add(int code, String location) {
        codePageMap.put(code, location);
    }

    public void add(Class cls, String location) {
        classPageMap.put(cls, location);
    }

    public String find(int code) {
        return codePageMap.get(code);
    }

    public String find(Class cls) {
        for(Class<?> kClass : classPageMap.keySet()) {
            if(cls.isAssignableFrom(kClass))
                return classPageMap.get(kClass);
        }
        return null;
    }
}
