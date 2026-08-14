package com.bifrost.core.audio;

import com.bifrost.common.util.Strings;
import com.github.stuxuhai.jpinyin.PinyinHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 拼音索引分组（indexLetter）。
 *
 * <p>算法：首字符为 ASCII 字母 → 大写；汉字 → jpinyin 短语词典取拼音首字母（失败 '#'）；
 * 其他（日文/韩文/数字/符号）→ '#'。见《音乐管理技术设计》§5、《音乐管理功能说明》§5。</p>
 */
@Slf4j
public final class PinyinIndex {

    /** 兜底分组（非拉丁/转换失败） */
    public static final String FALLBACK_LETTER = "#";

    private PinyinIndex() {
    }

    /**
     * 计算索引分组字母。
     *
     * @param name 艺术家名称（可空）
     * @return 大写字母 A–Z 或 '#'
     */
    public static String indexLetter(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null || trimmed.isEmpty()) {
            return FALLBACK_LETTER;
        }
        char first = trimmed.charAt(0);
        // 拉丁字母：不区分大小写，归大写组
        if (first >= 'A' && first <= 'Z' || first >= 'a' && first <= 'z') {
            return String.valueOf(Character.toUpperCase(first));
        }
        // 汉字：取拼音首字母
        if (first >= 0x4E00 && first <= 0x9FFF) {
            try {
                String pinyinInitials = PinyinHelper.getShortPinyin(trimmed);
                if (pinyinInitials != null && !pinyinInitials.isEmpty()) {
                    char c = pinyinInitials.charAt(0);
                    if (c >= 'a' && c <= 'z') {
                        return String.valueOf(Character.toUpperCase(c));
                    }
                }
            } catch (Exception e) {
                log.debug("拼音转换失败，归入 # 组: {}", name, e);
            }
            return FALLBACK_LETTER;
        }
        // 其他字符
        return FALLBACK_LETTER;
    }
}
