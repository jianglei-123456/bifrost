package com.bifrost.core.book.sync;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.SyncAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 启动时确保同步账号存在（M3-sync T1.4）。
 *
 * <p>首启自动创建账号 {@code reader} + <b>随机口令</b>（R2a/R2b 的前提：设备端 Register/Login 立刻可用，
 * 口令由管理端「阅读进度」页回显给用户）。随机口令天然满足 R2c（≠ 管理员口令）。</p>
 *
 * <p>幂等：已有账号时什么都不做。协议开关关闭时跳过。</p>
 *
 * <p>⚠️ <b>必须是 {@link InitializingBean}（在 Web 容器开始接受请求之前完成），不能是
 * {@code ApplicationRunner}</b>：ApplicationRunner 在容器 refresh 之后才跑，此时端口已经在收请求——
 * 若有请求在这个窗口里打到同步端点，它会"看不到账号"从而自己发起 INSERT，而初始化器的 INSERT
 * 尚未提交，两个写撞在一起就是 {@code SQLITE_BUSY}（实测过：管理端刚启动就刷新「阅读进度」页会 500）。
 * 与 {@code AdminUserInitializer} 同款：初始化写必须在收流量之前落库。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAccountInitializer implements InitializingBean {

    private final SyncAccountService syncAccountService;
    private final BifrostProperties properties;

    @Override
    public void afterPropertiesSet() {
        if (!properties.getKosync().isEnabled()) {
            return;
        }
        try {
            SyncAccount account = syncAccountService.currentOrCreate();
            log.info("阅读进度同步已启用（协议端点挂根路径 /users/**、/syncs/**、/healthcheck；同步账号: {}）",
                    account.getUsername());
        } catch (RuntimeException e) {
            // 不阻断启动：账号缺失时管理端首次访问会自愈重建
            log.warn("同步账号初始化失败（不影响启动）: {}", e.toString());
        }
    }
}
