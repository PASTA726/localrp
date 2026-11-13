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
    /** Optional local profile photo (private copy path). Empty = derived letter avatar. */
    String avatarPath = "";
    /** Optional per-character sampler temperature override (provider path); "" = follow chat/global. */
    String samplerTemp = "";
}
