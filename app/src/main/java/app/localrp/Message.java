package app.localrp;

/** One message inside a conversation. Persisted by {@link PersistenceCodec}. */
class Message {
    String speaker, text;
    boolean user;
    long at;

    Message(String s, String t, boolean u) { this(s, t, u, System.currentTimeMillis()); }
    Message(String s, String t, boolean u, long at) { speaker = s; text = t; user = u; this.at = at; }
}
