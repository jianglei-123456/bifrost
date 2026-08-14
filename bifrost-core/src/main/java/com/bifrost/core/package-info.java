/**
 * Bifrost 核心业务逻辑层（Service）。
 *
 * <p>按媒体类型做包隔离，避免各领域逻辑相互耦合：</p>
 * <ul>
 *   <li>{@code video} — 视频扫描、索引逻辑</li>
 *   <li>{@code audio} — 音频标签解析</li>
 *   <li>{@code book} — 电子书元数据解析</li>
 * </ul>
 */
package com.bifrost.core;
