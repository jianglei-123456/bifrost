package com.bifrost.core.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 拼音索引分组单测（《音乐管理技术设计》§5、《音乐管理功能说明》§5）。
 */
class PinyinIndexTest {

    @Test
    void latinLetterUppercased() {
        assertEquals("A", PinyinIndex.indexLetter("ABBA"));
        assertEquals("B", PinyinIndex.indexLetter("beatles"));
        assertEquals("Z", PinyinIndex.indexLetter("zhu"));
    }

    @Test
    void chinesePinyinInitial() {
        assertEquals("Z", PinyinIndex.indexLetter("周杰伦"));
        assertEquals("C", PinyinIndex.indexLetter("重庆")); // 多音字短语词典消歧
        assertEquals("W", PinyinIndex.indexLetter("未知"));
    }

    @Test
    void fallbackForOtherChars() {
        assertEquals("#", PinyinIndex.indexLetter("2Pac"));
        assertEquals("#", PinyinIndex.indexLetter("123"));
        assertEquals("#", PinyinIndex.indexLetter("あいう"));
        assertEquals("#", PinyinIndex.indexLetter("한국"));
        assertEquals("#", PinyinIndex.indexLetter("!@#"));
        assertEquals("#", PinyinIndex.indexLetter(null));
        assertEquals("#", PinyinIndex.indexLetter(""));
    }

    @Test
    void polyphonicCharacterNeverFallsToFallback() {
        // 多音字按词典转换，不应落入 '#'（个别字读音与常用语序相关，见设计可增强：人工纠错表）
        assertEquals("D", PinyinIndex.indexLetter("单"));   // 单(dan)
        assertEquals("C", PinyinIndex.indexLetter("曾"));   // jpinyin 词典取 曾(ceng)
    }
}
