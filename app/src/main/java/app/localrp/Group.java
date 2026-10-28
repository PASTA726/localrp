package app.localrp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A conversation (single-character or group roleplay). Persisted by {@link PersistenceCodec}. */
class Group {
    String id = UUID.randomUUID().toString();
    String name = "";
    boolean roleplay = true;
    List<String> characterIds = new ArrayList<>();
}
