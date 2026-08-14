package com.bifrost.api.response;

import java.util.List;

/**
 * 分页结果 {@code {total, items}}。
 *
 * <p>分页参数约定：page（0 起）/ size（默认 20，最大 200）。《通用功能说明》§9.1。</p>
 *
 * @param total 总数
 * @param items 当前页数据
 */
public record PageResult<T>(long total, List<T> items) {
}
