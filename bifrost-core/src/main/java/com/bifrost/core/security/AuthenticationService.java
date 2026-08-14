package com.bifrost.core.security;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 统一认证服务（/api/** 与 /rest/** 共用，同一账号）。
 *
 * <p>口令以 AES-GCM 可逆存储；令牌验证 = md5(解密口令 + salt) 恒定时间比较（Q21）。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordCipher passwordCipher;

    /** 密码认证（明文比对）。 */
    public Optional<User> authenticateByPassword(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).filter(u -> {
            try {
                return passwordCipher.decrypt(u.getEncryptedPassword()).equals(password);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /** 令牌认证（t/s，恒定时间比较）。 */
    public Optional<User> authenticateByToken(String username, String token, String salt) {
        if (username == null || token == null || salt == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username).filter(u -> {
            try {
                String plain = passwordCipher.decrypt(u.getEncryptedPassword());
                return SubsonicTokenUtil.constantTimeEquals(SubsonicTokenUtil.token(plain, salt), token);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /** 修改密码（校验旧密码）。 */
    public void changePassword(String username, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw BizException.paramError("新密码不能为空");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("未认证"));
        try {
            if (!passwordCipher.decrypt(user.getEncryptedPassword()).equals(oldPassword)) {
                throw BizException.unauthorized("旧密码不正确");
            }
        } catch (IllegalStateException e) {
            throw BizException.unauthorized("旧密码不正确");
        }
        user.setEncryptedPassword(passwordCipher.encrypt(newPassword));
        userRepository.save(user);
    }
}
