package app.localrp;

/**
 * Pure diagnostics helpers (v0.20.01): formats the last-crash record the global
 * uncaught-exception handler stores in SharedPreferences and the human-readable
 * report shown on the Settings card. No Android imports — JVM-tested.
 */
final class Diagnostics {
    private Diagnostics() {
    }

    /** A captured crash: when, what, and the readable top of the stack. */
    static final class CrashRecord {
        final long at;
        final String type;
        final String message;
        final String stack;

        CrashRecord(long at, String type, String message, String stack) {
            this.at = at;
            this.type = type;
            this.message = message;
            this.stack = stack;
        }
    }

    /** Builds a compact record from a throwable; lengths are capped so prefs stay small. */
    static CrashRecord fromThrowable(long now, Throwable throwable) {
        if (throwable == null) {
            return new CrashRecord(now, "unknown", "", "");
        }
        return new CrashRecord(now, throwable.getClass().getName(),
                cap(throwable.getMessage(), 300), stackTail(throwable.getStackTrace(), 8));
    }

    /** Top frames, one per line, capped — the tail of a stack rarely helps here. */
    static String stackTail(StackTraceElement[] frames, int max) {
        StringBuilder out = new StringBuilder();
        if (frames == null) return "";
        for (int i = 0; i < frames.length && i < max; i++) {
            if (out.length() > 0) out.append('\n');
            out.append("  · ").append(frames[i].getClassName()).append('.')
                    .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
        }
        return out.toString();
    }

    private static String cap(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    /** Human-readable device + crash report for the Settings card and the clipboard. */
    static String buildReport(String versionName, int sdkInt, String deviceModel,
                              long freeBytes, CrashRecord crash) {
        StringBuilder out = new StringBuilder();
        out.append("LocalRP ").append(versionName);
        out.append(" · Android ").append(sdkInt);
        out.append(" · ").append(deviceModel);
        out.append(" · ").append(freeBytes / (1024L * 1024L)).append(" MB boş depolama");
        if (crash != null) {
            out.append("\nSon çökme: ").append(crash.type);
            if (!crash.message.isEmpty()) out.append(" — ").append(crash.message);
            if (!crash.stack.isEmpty()) out.append('\n').append(crash.stack);
        }
        return out.toString();
    }
}
