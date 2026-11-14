package app.localrp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure roleplay prompt assembly, extracted from MainActivity (Task 07): every byte of
 * the engine prompt is derived from the inputs here, with no Android framework calls,
 * so the exact prompt shape of v0.7.3's group-RP identity fix stays test-regulated.
 * Prompt format is user-visible behavior for the model; change it only deliberately.
 */
final class RoleplayPrompt {
    private RoleplayPrompt() {}

    /**
     * @param sceneName group/conversation name as typed by the user
     * @param speaker the character that must answer this turn
     * @param castIds cast ids in scene order (group.characterIds)
     * @param castLookup resolves a cast id to its profile (null = character deleted)
     * @param history the stored transcript (may be empty)
     * @param continuing true when the engine still holds this conversation in context
     * @param continuation true for continuing/retry turns that force a fresh speaker
     */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget, null, false);
    }

    /**
     * @param persona optional user persona: {name, description} (may be null; length-2)
     */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget, persona, false);
    }
    /**
     * @param english true = assemble the whole engine prompt in English and instruct the
     *                model to reply in English (English-tuned models roleplay better).
     *                false = the byte-identical Turkish prompt of earlier versions.
     * @param lorebook world book entries (DeepSeek item 12); may be null. Triggered
     *                 entries are injected on fresh builds only (continuing turns
     *                 already carry the block inside the engine's saved context).
     */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona, boolean english) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget,
                persona, english, null);
    }

    /** Full overload: identity, persona, world book (lore) and history assembly. */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona, boolean english,
                        List<Lorebook.Entry> lorebook) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget,
                persona, english, lorebook, null);
    }

    /**
     * @param steer optional one-shot steering note ("Yönlendirerek yeniden üret",
     *              v0.19.22): guides only the reply this prompt builds — callers
     *              consume it right after use, so later turns rebuild without it.
     */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona, boolean english,
                        List<Lorebook.Entry> lorebook, String steer) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget,
                persona, english, lorebook, steer, null, null);
    }

    /**
     * Full overload with memory (v0.23.00 "Gelişmiş hafıza"): {@code recap} is the
     * extractive summary of transcript beyond the history window and
     * {@code memories} the relevant pinned events; both are injected on fresh
     * builds only (continuing turns already carry them inside the engine's
     * saved context), like the lorebook.
     */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona, boolean english,
                        List<Lorebook.Entry> lorebook, String steer,
                        String recap, List<String> memories) {
        return build(sceneName, speaker, castIds, castLookup, history, continuing, continuation,
                aiInstructions, themeName, themeInstruction, historyDepth, historyCharBudget,
                persona, english, lorebook, steer, recap, memories, false);
    }

    /** Full build with the think-mode block (v0.28.00): see {@code thinkBlock}. */
    static String build(String sceneName, CharacterProfile speaker, List<String> castIds,
                        Function<String, CharacterProfile> castLookup, List<Message> history,
                        boolean continuing, boolean continuation,
                        String aiInstructions, String themeName, String themeInstruction,
                        int historyDepth, int historyCharBudget, String[] persona, boolean english,
                        List<Lorebook.Entry> lorebook, String steer,
                        String recap, List<String> memories, boolean think) {
        StringBuilder prompt = new StringBuilder();
        String speakerName = clip(speaker.name, 80);
        Message last = history.isEmpty() ? null : history.get(history.size() - 1);

        if (aiInstructions != null && !aiInstructions.isEmpty()) {
            prompt.append(english ? "General AI instruction: " : "Genel AI talimatı: ").append(aiInstructions).append("\n");
        }
        if (themeInstruction != null && !themeInstruction.isEmpty()) {
            prompt.append(english ? "Scene tone (" : "Sahne teması (").append(themeName == null ? "" : themeName).append("): ")
                    .append(themeInstruction).append("\n");
        }
        if (steer != null && !steer.trim().isEmpty()) {
            prompt.append(english ? "Steering note (this reply only): " : "Yönlendirme notu (yalnızca bu yanıt): ")
                    .append(oneLine(steer, 300)).append("\n");
        }
        if (think) {
            // Think yeteneği (v0.28.00): the model anchors the scene silently BEFORE
            // writing — fights fast topic drift on fresh AND continuing turns.
            prompt.append(english
                    ? "Think mode: first write a short silent reasoning block wrapped in <think> and </think> tags: what is this scene about, what happened in the last few messages, which topic is still running, and what does your character want this turn? Bind your reply tightly to that topic and to the earlier messages. Then close the </think> tag and write ONLY the in-character reply after it; no notes outside the tags.\n"
                    : "Düşünme modu: Önce kısa bir sessiz akıl yürütme bloğu yaz, bloğu <think> etiketiyle aç ve </think> etiketiyle kapat: bu sahne neyle ilgili, son birkaç mesajda ne oldu, hangi konu hâlâ sürüyor ve karakterin bu turdaki amacı ne? Yanıtını o konuya ve önceki mesajlara sıkı şekilde bağla. Sonra </think> etiketini kapat ve yalnızca rol içi yanıtı yaz; etiketlerin dışında karakter dışı açıklama yapma.\n");
        }
        prompt.append(english ? "Scene: " : "Sahne: ").append(oneLine(sceneName, 160)).append("\n");
        if (english) {
            prompt.append("This turn, reply speaking ONLY as ").append(speakerName).append(".\n");
        } else {
            prompt.append("Bu tur yalnızca ").append(speakerName).append(" karakterini konuşacak şekilde yanıt ver.\n");
        }
        if (!continuing) {
            // World book (DeepSeek item 12): only entries whose keys match the last few
            // transcript lines are injected, under a fixed character budget.
            if (lorebook != null && !lorebook.isEmpty()) {
                StringBuilder scan = new StringBuilder();
                for (int i = Math.max(0, history.size() - 3); i < history.size(); i++) {
                    Message scanMessage = history.get(i);
                    if (scanMessage.text != null && !scanMessage.text.isEmpty()) {
                        scan.append(scanMessage.text).append("\n");
                    }
                }
                String scanText = scan.length() > 1600 ? scan.substring(scan.length() - 1600) : scan.toString();
                List<Lorebook.Entry> triggered = Lorebook.triggered(lorebook, scanText);
                if (!triggered.isEmpty()) {
                    prompt.append(english
                            ? "World book (facts matching keywords in the scene):\n"
                            : "Dünya kitabı (sahnede geçen anahtar kelimelerle tetiklenen olgular):\n");
                    prompt.append(Lorebook.assemble(triggered, Lorebook.BLOCK_CHAR_BUDGET));
                }
            }
            String description = clip(speaker.description, 700);
            String personality = clip(speaker.personality, 900);
            String instructions = clip(speaker.instructions, 900);
            String examples = clip(speaker.examples, 1200);
            if (!description.isEmpty())
                prompt.append(english ? "Character description: " : "Karakter tanımı: ").append(description).append("\n");
            if (!personality.isEmpty())
                prompt.append(english ? "Personality and speech style: " : "Kişilik ve konuşma tarzı: ").append(personality).append("\n");
            if (!instructions.isEmpty())
                prompt.append(english ? "Character instructions: " : "Karakter talimatları: ").append(instructions).append("\n");
            if (!examples.isEmpty())
                prompt.append(english ? "Example dialogue:\n" : "Örnek diyalog:\n").append(examples).append("\n");

            prompt.append(english ? "Other characters in the scene:\n" : "Sahnedeki diğer karakterler:\n");
            boolean otherCharacter = false;
            for (String id : castIds) {
                CharacterProfile character = castLookup.apply(id);
                if (character == null || character.id.equals(speaker.id)) continue;
                otherCharacter = true;
                prompt.append("- ").append(clip(character.name, 80));
                String detail = !character.description.trim().isEmpty() ? character.description : character.personality;
                if (!detail.trim().isEmpty()) prompt.append(": ").append(oneLine(detail, 220));
                prompt.append("\n");
            }
            if (!otherCharacter) prompt.append(english ? "- None (solo scene)\n" : "- Yok (bire bir sahne)\n");

            appendPersona(prompt, persona, english);

            // Memory (v0.23.00): the automatic recap of what fell outside the
            // history window, then the pinned events relevant to this turn.
            if (recap != null && !recap.isEmpty()) {
                prompt.append(english
                        ? "\nStory so far (earlier events, summarized):\n"
                        : "\nHikâyede şimdiye kadar olanlar (eski olayların özeti):\n")
                        .append(recap).append("\n");
            }
            if (memories != null && !memories.isEmpty()) {
                prompt.append(english
                        ? "Remembered important events:\n"
                        : "Hatırlanan önemli anlar:\n");
                for (String memory : memories) {
                    if (memory != null && !memory.isEmpty()) {
                        prompt.append("- ").append(oneLine(memory, ChatMemory.MAX_ENTRY_CHARS)).append("\n");
                    }
                }
            }

            if (historyDepth > 0 && history.size() > 1) {
                // historyDepth 0 is the online provider's system prompt (DeepSeek item 14/B):
                // history travels as real chat turns, so a bare header would dangle empty.
                // v0.27.00: the header itself is emitted only when at least one line
                // fits the budget — an empty heading wastes tokens and misleads the model.
                String historyBlock = historyBlock(history, last != null && last.user, historyDepth, historyCharBudget, english);
                if (!historyBlock.isEmpty()) {
                    prompt.append(english ? "\nRecent scene history:\n" : "\nYakın sahne geçmişi:\n")
                            .append(historyBlock);
                }
            }
        } else {
            // The engine keeps ONE shared context per conversation; on continuing turns the
            // model would otherwise stay anchored to the previous speaker's persona.
            // Re-inject this turn's identity compactly. (Kullanıcı kusuru: grup RP'de rol kayması)
            prompt.append(english
                    ? "Context continues; keep the scene, but this turn's character identity is below.\n"
                    : "Bağlam devam ediyor; sahneyi koru ama bu turun karakter kimliği aşağıdadır.\n");
            String hint = clip(speaker.description, 160);
            if (hint.isEmpty()) hint = clip(speaker.personality, 160);
            prompt.append(english ? "Role identity: " : "Rol kimliği: ").append(speakerName);
            if (!hint.isEmpty()) prompt.append(" — ").append(hint);
            prompt.append("\n");
            if (persona != null) appendPersona(prompt, persona, english);
            StringBuilder otherNames = new StringBuilder();
            int otherBudget = 280;
            for (String id : castIds) {
                CharacterProfile character = castLookup.apply(id);
                if (character == null || character.id.equals(speaker.id)) continue;
                String name = clip(character.name.trim(), 60);
                if (name.isEmpty() || otherBudget <= name.length()) continue;
                if (otherNames.length() > 0) { otherNames.append(", "); otherBudget -= 2; }
                otherNames.append(name);
                otherBudget -= name.length();
            }
            if (otherNames.length() > 0) {
                prompt.append(english
                        ? "Other characters (do not write their lines): "
                        : "Sahnedeki diğer karakterler (adlarına yazma): ").append(otherNames).append("\n");
            }
        }
        if (last != null && last.user) {
            prompt.append(english ? "\nThe user's last message: " : "\nKullanıcının son mesajı: ")
                    .append(clip(last.text, 3000)).append("\n");
        } else if (continuation) {
            prompt.append(english
                    ? "\nThe scene continues; react naturally to this moment as the next character.\n"
                    : "\nSahne devam ediyor; sıradaki karakter olarak bu ana doğal bir tepki ver.\n");
        }
        if (english) {
            prompt.append("\nRules: Write one in-character reply ONLY as ").append(speakerName)
                    .append(". Lines labeled \"User:\" in the history are the user's own words; do not ")
                    .append("speak for the user or other characters. Do not begin your reply with the ")
                    .append("character's name. Reply in English and stay within the scene.");
        } else {
            prompt.append("\nKurallar: Yalnızca ").append(speakerName)
                    .append(" olarak tek bir rol içi yanıt yaz. Geçmişte \"Kullanıcı:\" olarak görünen satırlar kullanıcının " +
                            "kendi sözleridir; kullanıcı veya diğer karakterler adına konuşma. ")
                    .append("Yanıtın başında karakter adını yazma. Türkçe yanıt ver ve sahnenin dışına çıkma.");
        }
        return prompt.toString();
    }

    /** User identity block: v0.14.0 feature; omitted entirely when no persona is set. */
    private static void appendPersona(StringBuilder prompt, String[] persona, boolean english) {
        if (persona == null) return;
        String name = clip(persona.length > 0 ? persona[0] : "", 80);
        String description = clip(persona.length > 1 ? persona[1] : "", 400);
        prompt.append(english ? "User identity: " : "Kullanıcı kimliği: ").append(name.isEmpty() ? (english ? "You" : "Sen") : name);
        if (!description.isEmpty()) prompt.append(" — ").append(oneLine(description, 400));
        prompt.append("\n");
    }

    /** Builds the history lines; the caller emits the header only when non-empty. */
    private static String historyBlock(List<Message> history, boolean skipLastUser,
                                       int historyDepth, int historyCharBudget, boolean english) {
        StringBuilder out = new StringBuilder();
        appendHistory(out, history, skipLastUser, historyDepth, historyCharBudget, english);
        return out.toString();
    }

    private static void appendHistory(StringBuilder prompt, List<Message> history, boolean skipLastUser,
                                      int historyDepth, int historyCharBudget, boolean english) {
        int end = history.size() - (skipLastUser ? 1 : 0);
        int start = Math.max(0, end - historyDepth);
        List<String> lines = new ArrayList<>();
        int budget = historyCharBudget;
        for (int i = end - 1; i >= start; i--) {
            Message message = history.get(i);
            // User records are stored with the display label "Sen"; the transcript must
            // disambiguate the user from any character, so the prompt label is explicit
            // ("Kullanıcı" in Turkish mode, "User" in English mode).
            String who = message.user ? (english ? "User" : "Kullanıcı") : message.speaker;
            // Image-only records carry no words; a placeholder keeps the transcript
            // from reading as "Speaker: " with nothing after it.
            String body = (message.text == null || message.text.isEmpty()) && message.hasImage()
                    ? (english ? "[image]" : "[görsel]") : (message.text == null ? "" : message.text);
            String line = who + ": " + body;
            if (line.length() > budget) {
                if (budget > 40) lines.add(0, line.substring(0, budget - 1) + "…");
                break;
            }
            lines.add(0, line);
            budget -= line.length() + 1;
        }
        for (String line : lines) prompt.append(line).append("\n");
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String oneLine(String value, int max) {
        if (value == null) return "";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 1)) + "…";
    }
}
