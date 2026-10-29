package app.localrp;

import java.util.regex.Pattern;

/**
 * Pure response cleanup, extracted verbatim from MainActivity (task G0): the exact
 * same helper is used during streaming renders and for the final stored message, so
 * the model's echoed speaker label ("<Ad>: ") and special-token residue are never
 * shown in an interim balloon either. No Android framework calls.
 */
final class ResponseCleanup {
    private ResponseCleanup() {}

    static String clean(String raw, String speakerName) {
        if (raw == null) return "";
        String text = raw.trim();
        text = text.replace("<|im_end|>", "").replace("<|eot_id|>", "")
                .replace("<|end_of_text|>", "").replace("</s>", "").trim();
        if (!text.isEmpty() && speakerName != null && !speakerName.trim().isEmpty()) {
            String name = Pattern.quote(speakerName.trim());
            // Optional delimiter: "Name:", "Name-", "Name", and asterisk-wrapped forms
            // ("*Name*: x", "*Name* x") all count as an echoed label (task G0).
            text = text.replaceFirst("(?is)^\\s*\\*{0,2}" + name + "\\*{0,2}([:：-]\\s*)?", "");
        }
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }
}
