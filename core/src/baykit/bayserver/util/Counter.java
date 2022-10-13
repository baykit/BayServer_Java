package baykit.bayserver.util;

public class Counter {

    private int counter;

    public Counter() {
        this(1);
    }

    public Counter(int counter) {
        this.counter = counter;
    }

    public synchronized int next() {
        return counter++;
    }
}
