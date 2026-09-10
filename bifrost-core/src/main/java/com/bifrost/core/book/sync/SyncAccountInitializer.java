package com.bifrost.core.book.sync;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.SyncAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保同步账号存在（M3-sync T1.4）。
 *
 * <p>首启自动创建账号 {@code reader} + <b>随机口令</b>（R2a/R2b 的前提：设备端 Register/Login 立刻可用，
 * 口令由管理端「阅读进度」页回显给用户）。随机口令天然满足 R2c（≠ 管理员口令）。</p>
 *
 * <p>幂等：已有账号时什么都不做。协议开关关闭时跳过。</p>
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SyncAccountInitializer implements ApplicationRunner {

    private final SyncAccountService syncAccountService;
    private final BifrostProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getKosync().isEnabled()) {
            return;
        }
        try {
            SyncAccount account = syncAccountService.currentOrCreate();
            log.info("阅读进度同步已启用（协议端点挂根路径 /users/**、/syncs/**、/healthcheck；同步账号: {}）",
                    account.getUsername());
        } catch (RuntimeException e) {
            log.warn("同步账号初始化失败（不影响启动）: {}", e.toString());
        }
    }
}
