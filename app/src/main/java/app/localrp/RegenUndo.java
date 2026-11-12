package app.localrp;

import java.util.List;

/** Pure decision core for the regenerate-undo comfort tool (v0.19.22): decides
 *  whether a stashed pre-regenerate tail can still be restored into the live
 *  transcript. MainActivity owns the Android side (stash storage, toasts, I/O);
 *  this class only answers the restore-safety question so it can be JVM-tested. */
class RegenUndo {
    private RegenUndo() {}

    /**
     * @param index      transcript index where the stashed tail began
     * @param anchorText text of the message just before the stash (null only when
     *                   index == 0); the live transcript must still agree with it
     * @param anchorAt   timestamp of that message (0 = unknown/legacy; skipped)
     * @param current    the live transcript
     * @return true when restoring the stash may proceed
     */
    static boolean canRestore(int index, String anchorText, long anchorAt, List<Message> current) {
        if (current == null || index < 0 || index > current.size()) return false;
        if (index > 0) {
            Message before = current.get(index - 1);
            if (anchorText == null ? before.text != null : !anchorText.equals(before.text)) return false;
            if (anchorAt > 0 && before.at != anchorAt) return false;
        } else if (anchorText != null || anchorAt > 0) {
            return false;
        }
        // User turns written after the regenerate must never be destroyed by an undo.
        for (int i = index; i < current.size(); i++) {
            if (current.get(i).user) return false;
        }
        return true;
    }
}
