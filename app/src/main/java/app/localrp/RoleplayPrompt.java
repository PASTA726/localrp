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
        StringBuilder prompt = new StringBuilder();
        String speakerName = clip(speaker.name, 80);
        Message last = history.isEmpty() ? null : history.get(history.size() - 1);

        if (aiInstructions != null && !aiInstructions.isEmpty()) {
            prompt.append("Genel AI talimatı: ").append(aiInstructions).append("\n");
        }
        if (themeInstruction != null && !themeInstruction.isEmpty()) {
            prompt.append("Sahne teması (").append(themeName == null ? "" : themeName).append("): ")
                    .append(themeInstruction).append("\n");
        }
        prompt.append("Sahne: ").append(oneLine(sceneName, 160)).append("\n");
        prompt.append("Bu tur yalnızca ").append(speakerName).append(" karakterini konuşacak şekilde yanıt ver.\n");
        if (!continuing) {
            String description = clip(speaker.description, 700);
            String personality = clip(speaker.personality, 900);
            String instructions = clip(speaker.instructions, 900);
            String examples = clip(speaker.examples, 1200);
            if (!description.isEmpty()) prompt.append("Karakter tanımı: ").append(description).append("\n");
            if (!personality.isEmpty()) prompt.append("Kişilik ve konuşma tarzı: ").append(personality).append("\n");
            if (!instructions.isEmpty()) prompt.append("Karakter talimatları: ").append(instructions).append("\n");
            if (!examples.isEmpty()) prompt.append("Örnek diyalog:\n").append(examples).append("\n");

            prompt.append("Sahnedeki diğer karakterler:\n");
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
            if (!otherCharacter) prompt.append("- Yok (bire bir sahne)\n");

            if (history.size() > 1) {
                prompt.append("\nYakın sahne geçmişi:\n");
                appendHistory(prompt, history, last != null && last.user, historyDepth, historyCharBudget);
            }
        } else {
            // The engine keeps ONE shared context per conversation; on continuing turns the
            // model would otherwise stay anchored to the previous speaker's persona.
            // Re-inject this turn's identity compactly. (Kullanıcı kusuru: grup RP'de rol kayması)
            prompt.append("Bağlam devam ediyor; sahneyi koru ama bu turun karakter kimliği aşağıdadır.\n");
            String hint = clip(speaker.description, 160);
            if (hint.isEmpty()) hint = clip(speaker.personality, 160);
            prompt.append("Rol kimliği: ").append(speakerName);
            if (!hint.isEmpty()) prompt.append(" — ").append(hint);
            prompt.append("\n");
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
                prompt.append("Sahnedeki diğer karakterler (adlarına yazma): ").append(otherNames).append("\n");
            }
        }
        if (last != null && last.user) {
            prompt.append("\nKullanıcının son mesajı: ").append(clip(last.text, 3000)).append("\n");
        } else if (continuation) {
            prompt.append("\nSahne devam ediyor; sıradaki karakter olarak bu ana doğal bir tepki ver.\n");
        }
        prompt.append("\nKurallar: Yalnızca ").append(speakerName)
                .append(" olarak tek bir rol içi yanıt yaz. Geçmişte \"Kullanıcı:\" olarak görünen satırlar kullanıcının " +
                        "kendi sözleridir; kullanıcı veya diğer karakterler adına konuşma. ")
                .append("Yanıtın başında karakter adını yazma. Türkçe yanıt ver ve sahnenin dışına çıkma.");
        return prompt.toString();
    }

    private static void appendHistory(StringBuilder prompt, List<Message> history, boolean skipLastUser,
                                      int historyDepth, int historyCharBudget) {
        int end = history.size() - (skipLastUser ? 1 : 0);
        int start = Math.max(0, end - historyDepth);
        List<String> lines = new ArrayList<>();
        int budget = historyCharBudget;
        for (int i = end - 1; i >= start; i--) {
            Message message = history.get(i);
            // User records are stored with the display label "Sen"; the transcript must
            // disambiguate the user from any character, so the prompt uses "Kullanıcı".
            String who = message.user ? "Kullanıcı" : message.speaker;
            String line = who + ": " + message.text;
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
