package yokohama.baykit.bayserver.util;

public interface DataConsumeListener {
    /**
     * Called when the unit's data has been consumed by the underlying write.
     *
     * @param bufferAvailable whether the internal write buffer still has room
     *                        at the time of consumption. See RudderState.bufferAvailable().
     */
    void dataConsumed(boolean bufferAvailable);
}
