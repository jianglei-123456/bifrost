package com.bifrost.api.dto.booksync;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * "重新匹配"结果（M3-sync T3.4）。
 *
 * <p>{@code matched=false} 是<b>正常结果</b>而不是错误——意思是"这本书确实还不在库里"，
 * 管理端应提示"仍未匹配到图书"，不要当失败弹错。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RematchView(boolean matched, ReadingProgressView progress) {
}
