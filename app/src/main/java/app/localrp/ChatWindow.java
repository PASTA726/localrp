package app.localrp;

/**
 * Pure arithmetic for the chat render window (Task 08): a long conversation must not
 * create a view row for every stored message when the screen opens. Only the newest
 * slice is rendered; older slices are surfaced upward on user demand.
 */
final class ChatWindow {
    private ChatWindow() {}

    /** Index of the first message rendered on a fresh open (0 when nothing is deferred). */
    static int initialStart(int size, int maxInitial) {
        return size > maxInitial ? size - maxInitial : 0;
    }

    /** Next chunk start when the user asks for older messages. */
    static int olderStart(int currentStart, int chunk) {
        return Math.max(0, currentStart - chunk);
    }
}
