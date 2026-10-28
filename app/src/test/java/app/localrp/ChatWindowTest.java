package app.localrp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Render-window arithmetic for long chats (Task 08). Windowing must never hide a
 * message permanently: the newest slice always renders, older slices stay reachable.
 */
public class ChatWindowTest {

    @Test
    public void smallChatsRenderEverythingFromZero() {
        assertEquals(0, ChatWindow.initialStart(0, 40));
        assertEquals(0, ChatWindow.initialStart(1, 40));
        assertEquals(0, ChatWindow.initialStart(40, 40));
    }

    @Test
    public void firstMessageBeyondWindowIsDeferred() {
        assertEquals(1, ChatWindow.initialStart(41, 40));
        // 100 stored messages → newest 40 rendered, oldest 60 behind the marker
        assertEquals(60, ChatWindow.initialStart(100, 40));
    }

    @Test
    public void olderChunksNeverCrossTheTop() {
        assertEquals(20, ChatWindow.olderStart(60, 40));
        assertEquals(0, ChatWindow.olderStart(40, 40));
        assertEquals(0, ChatWindow.olderStart(10, 40));
    }
}
