package com.bifrost.common.constant;

/**
 * Bifrost 发布版本号（唯一来源）。
 *
 * <p>与父 POM 的 {@code <version>} 保持一致：发版时两者一起改（发版清单见
 * {@code doc/操作手册/05-Docker部署.md} 与 CHANGELOG）。对外可见处两处，
 * 都读这里，避免"协议字段里的版本号没人记得改"：</p>
 * <ul>
 *   <li>管理端 {@code GET /api/version}（Vue Dashboard 系统页 / 运维核对）；</li>
 *   <li>Subsonic 响应信封的 {@code serverVersion} 属性（OpenSubsonic）。</li>
 * </ul>
 *
 * <p>注意区分：{@code 1.16.1} 是 Subsonic <b>协议</b>版本，{@code 1.0.0} 是
 * 每个 Subsonic 端点首次出现的 API 版本，两者都不属于本类。</p>
 */
public final class BifrostVersion {

    /** 当前发布版本（语义化版本，不带 -SNAPSHOT） */
    public static final String VERSION = "1.0.0";

    private BifrostVersion() {
    }
}
