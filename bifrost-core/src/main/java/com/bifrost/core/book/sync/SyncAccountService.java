package com.bifrost.core.book.sync;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.security.PasswordCipher;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.SyncAccountRepository;
import com.bifrost.domain.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 同步账号服务（M3-sync T1.4）。
 *
 * <p>三件事：① 口令以 AES-GCM 可逆存储（R2a：管理端要回显明文；协议校验要还原口令再算 md5）；
 * ② 校验协议凭据 {@code x-auth-key}（= md5(口令)，**恒定时间比较**）；③ 强制同步口令
 * <b>不等于</b>管理员口令（R2c）。</p>
 *
 * <p>Day-one 只有一个账号（Q3-B）；表结构按账号维度设计，未来加多账号不改协议层。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncAccountService {

    /** 首次启动自动创建的同步用户名（可在管理端改名） */
    public static final String DEFAULT_USERNAME = "reader";

    /** 随机口令字符集：剔除易混字符 0 O 1 l I */
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_LENGTH = 16;

    private final SyncAccountRepository syncAccountRepository;
    private final UserRepository userRepository;
    private final PasswordCipher passwordCipher;
    private final BifrostProperties properties;

    private final SecureRandom random = new SecureRandom();

    /** 当前同步账号；不存在则创建（首次启动的幂等入口）。 */
    @Transactional
    public SyncAccount currentOrCreate() {
        return syncAccountRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    SyncAccount account = new SyncAccount();
                    account.setUsername(DEFAULT_USERNAME);
                    account.setEncryptedPassword(passwordCipher.encrypt(generatePassword()));
                    log.info("已创建同步账号: {}（口令见管理端「阅读进度」页）", DEFAULT_USERNAME);
                    return syncAccountRepository.save(account);
                });
    }

    /** 当前同步账号（不创建）。 */
    public Optional<SyncAccount> current() {
        return syncAccountRepository.findFirstByOrderByIdAsc();
    }

    /**
     * 更新账号（用户名 / 口令，均可选）。
     *
     * @throws BizException 1000 参数非法（含"同步口令不能与管理员口令相同"）
     */
    @Transactional
    public SyncAccount update(String username, String plainPassword) {
        SyncAccount account = currentOrCreate();
        if (username != null) {
            String normalized = username.trim();
            validateUsername(normalized);
            account.setUsername(normalized);
        }
        if (plainPassword != null) {
            validatePassword(plainPassword);
            assertNotAdminPassword(plainPassword);
            account.setEncryptedPassword(passwordCipher.encrypt(plainPassword));
        }
        return syncAccountRepository.save(account);
    }

    /** 生成随机口令并落库（R2a 的"生成"路径）。 */
    @Transactional
    public SyncAccount resetPassword() {
        SyncAccount account = currentOrCreate();
        account.setEncryptedPassword(passwordCipher.encrypt(generatePassword()));
        return syncAccountRepository.save(account);
    }

    /** 还原明文口令（管理端回显 / 协议校验）。 */
    public String revealPassword(SyncAccount account) {
        try {
            return passwordCipher.decrypt(account.getEncryptedPassword());
        } catch (RuntimeException e) {
            log.warn("同步账号口令解密失败: {}", account.getUsername());
            return null;
        }
    }

    /**
     * 协议校验：{@code x-auth-user} + {@code x-auth-key}（= 口令的 md5 小写 hex）。
     *
     * <p>失败原因（未知账号 / 口令不符）对调用方<b>不作区分</b>，避免账号枚举。</p>
     */
    public boolean verify(String username, String authKey) {
        return authenticate(username, authKey).isPresent();
    }

    /**
     * 校验并返回账号（协议过滤器用；成功时把账号 id 放进请求属性供控制器读取）。
     *
     * @return 凭据有效时的账号；否则 {@link Optional#empty()}
     */
    public Optional<SyncAccount> authenticate(String username, String authKey) {
        if (username == null || username.isBlank() || authKey == null || authKey.isBlank()) {
            return Optional.empty();
        }
        SyncAccount account = syncAccountRepository.findByUsername(username.trim()).orElse(null);
        if (account == null) {
            return Optional.empty();
        }
        String plain = revealPassword(account);
        if (plain == null) {
            return Optional.empty();
        }
        boolean ok = SubsonicTokenUtil.constantTimeEquals(
                SubsonicTokenUtil.token(plain, ""), authKey.trim().toLowerCase());
        return ok ? Optional.of(account) : Optional.empty();
    }

    /** 生成 16 位随机口令（不含易混字符）。 */
    public String generatePassword() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw BizException.paramError("同步用户名不能为空");
        }
        if (username.length() > 64) {
            throw BizException.paramError("同步用户名不能超过 64 字符");
        }
        if (username.contains(":")) {
            // 与官方一致：':' 会让用户名/key 的存储与解析产生歧义
            throw BizException.paramError("同步用户名不能包含 ':'");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw BizException.paramError("同步口令不能为空");
        }
        if (password.length() > 128) {
            throw BizException.paramError("同步口令不能超过 128 字符");
        }
    }

    /**
     * R2c：同步口令必须与管理员口令不同。
     *
     * <p>理由：协议凭据是口令的 md5（等价 bearer、不可加盐），把管理员口令交给每台阅读设备
     * 等于把管理端凭据交出去。</p>
     */
    private void assertNotAdminPassword(String plainPassword) {
        String candidate = SubsonicTokenUtil.token(plainPassword, "");
        for (User admin : adminUsers()) {
            String adminPlain = null;
            try {
                adminPlain = passwordCipher.decrypt(admin.getEncryptedPassword());
            } catch (RuntimeException ignored) {
                // 解不开就跳过（不因为它阻断用户改口令）
            }
            if (adminPlain != null
                    && SubsonicTokenUtil.constantTimeEquals(SubsonicTokenUtil.token(adminPlain, ""), candidate)) {
                throw BizException.paramError("同步口令不能与管理员口令相同");
            }
        }
    }

    private List<User> adminUsers() {
        List<User> admins = new ArrayList<>();
        userRepository.findByUsername(properties.getAuth().getInitialUsername()).ifPresent(admins::add);
        if (admins.isEmpty()) {
            admins.addAll(userRepository.findAll());
        }
        return admins;
    }
}
