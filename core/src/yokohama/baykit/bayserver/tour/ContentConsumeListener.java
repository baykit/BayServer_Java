package yokohama.baykit.bayserver.tour;

public interface ContentConsumeListener {
    void contentConsumed(int len, boolean resume);

    ContentConsumeListener devNull = new ContentConsumeListener() {
        @Override
        public void contentConsumed(int len, boolean resume) {

        }
    };
}
