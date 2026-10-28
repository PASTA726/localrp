package app.localrp;

import java.util.UUID;

/** A roleplay character created by the user. Persisted by {@link PersistenceCodec}. */
class CharacterProfile {
    String id = UUID.randomUUID().toString();
    String name = "";
    String description = "";
    String personality = "";
    String instructions = "";
    String examples = "";
}
