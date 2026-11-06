package app.localrp;

/** One message inside a conversation. Persisted by {@link PersistenceCodec}. */
class Message {
    String speaker, text;
    boolean user;
    long at;
    /** Optional: absolute path of an attached/generated image backing this message. */
    String image;

    Message(String s, String t, boolean u) { this(s, t, u, System.currentTimeMillis()); }
    Message(String s, String t, boolean u, long at) { speaker = s; text = t; user = u; this.at = at; }
    Message(String s, String t, boolean u, long at, String image) {
        speaker = s; text = t; user = u; this.at = at; this.image = image;
    }

    boolean hasImage() { return image != null && !image.isEmpty(); }
}
