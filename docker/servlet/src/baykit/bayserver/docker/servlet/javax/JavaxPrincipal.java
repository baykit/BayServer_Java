package baykit.bayserver.docker.servlet.javax;

import java.security.Principal;

class JavaxPrincipal implements Principal {

    private final String name;

    JavaxPrincipal(String name) {
        if (name == null)
            throw new NullPointerException();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return getName();
    }

    public boolean equals(Object o) {
        return (o instanceof Principal)
                && (getName().equals(((Principal) o).getName()));
    }
}