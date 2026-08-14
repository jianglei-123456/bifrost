package com.bifrost.core.security;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.enums.UserRole;
import com.bifrost.domain.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 初始管理员账号初始化（《音乐管理技术设计》§9）。
 *
 * <p>首次启动（无任何用户）时按配置创建 admin，密码来自环境变量
 * {@code BIFROST_AUTH_INITIAL_PASSWORD}（必填，不落 yml/仓库）；已存在则忽略。
 * 实现为 {@link InitializingBean}：在上下文刷新期间执行，先于 Web 服务器对外服务，
 * 避免"首请求先于建号"的竞态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements InitializingBean {

    private final UserRepository userRepository;
    private final PasswordCipher passwordCipher;
    private final BifrostProperties properties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (userRepository.count() > 0) {
            return;
        }
        String username = properties.getAuth().getInitialUsername();
        String password = environment.getProperty("BIFROST_AUTH_INITIAL_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "首次启动必须通过环境变量 BIFROST_AUTH_INITIAL_PASSWORD 设置初始管理员密码（不落配置文件）");
        }
        User user = new User();
        user.setUsername(username);
        user.setEncryptedPassword(passwordCipher.encrypt(password));
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        log.info("已创建初始管理员账号: {}", username);
    }
}
