package com.bifrost.core.book.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.ApplicationRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住同步账号初始化器的**时机**（M3-sync T1.4）。
 *
 * <p>这不是形式主义：{@code ApplicationRunner} 在 Web 容器开始接受请求**之后**才跑，
 * 若初始化写还没提交，启动窗口内到达的同步请求会"看不到账号"从而自己发起 INSERT，
 * 两个写撞上就是 {@code SQLITE_BUSY}——实测过：管理端刚启动就刷新「阅读进度」页，
 * {@code PUT /api/book-sync/account} 直接 500（1200）。
 * 所以这里断言它必须是 {@link InitializingBean}。</p>
 */
class SyncAccountInitializerTest {

    @Test
    void runsBeforeTheWebServerAcceptsTraffic() {
        assertTrue(InitializingBean.class.isAssignableFrom(SyncAccountInitializer.class),
                "同步账号初始化必须是 InitializingBean（容器 refresh 期间完成）");
        assertFalse(ApplicationRunner.class.isAssignableFrom(SyncAccountInitializer.class),
                "不能是 ApplicationRunner：它在端口已开之后才跑，启动窗口内的请求会撞 SQLITE_BUSY");
    }
}
