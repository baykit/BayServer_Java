package yokohama.baykit.bayserver.common;

public abstract class Vehicle {

    protected final int id;

    public Vehicle(int id) {
        this.id = id;
    }

    public abstract void run();
    protected abstract void onTimer();

}
