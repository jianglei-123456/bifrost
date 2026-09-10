package com.bifrost.api.dto.booksync;

/**
 * 同步账号视图（M3-sync T3.2）。
 *
 * <p>{@code password} 是<b>明文</b>（R2a：家庭自托管场景需要把它抄进阅读设备；后端用 AES-GCM
 * 可逆存储就是为了这个）。{@code serverUrl} 是拼好的、可直接抄进 KOReader 的地址——
 * <b>必须带 {@code http://}</b>：客户端的自定义服务器输入框预填 {@code https://} 且不自动补 scheme。</p>
 */
public record SyncAccountView(
        String username,
        String password,
        boolean registrationEnabled,
        boolean autoScanOnUnmatched,
        String serverUrl,
        boolean serverUrlHintConfigured) {
}
