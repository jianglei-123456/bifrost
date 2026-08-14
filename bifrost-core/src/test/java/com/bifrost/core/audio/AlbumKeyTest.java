package com.bifrost.core.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 专辑聚合键单测（《音乐管理功能说明》§4.2：trim + 小写折叠）。
 */
class AlbumKeyTest {

    @Test
    void normalizeTrimsAndFoldsCase() {
        assertEquals("the dark side of the moon|月之暗面",
                AlbumKey.key("  The Dark Side OF The Moon ", " 月之暗面 "));
    }

    @Test
    void nullPartsBecomeEmpty() {
        assertEquals("|未知标题", AlbumKey.key(null, "未知标题"));
        assertEquals("未知艺术家|", AlbumKey.key("未知艺术家", null));
        assertEquals("|", AlbumKey.key(null, null));
    }

    @Test
    void matchesIgnoresCaseAndWhitespace() {
        assertTrue(AlbumKey.matches("Abba", "Arrival", "ABBA", "  arrival "));
        assertFalse(AlbumKey.matches("Abba", "Arrival", "ABBA", "Super Trouper"));
    }
}
