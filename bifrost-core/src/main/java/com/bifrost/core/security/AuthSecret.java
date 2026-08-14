package com.bifrost.core.security;

import com.bifrost.core.config.BifrostProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 口令加密主密钥（AES-256）。
 *
 * <p>来源优先级：环境变量 {@code BIFROST_AUTH_SECRET}（任意字符串，SHA-256 派生 32 字节）；
 * 缺失则自动生成随机密钥写入数据目录 {@code secret.key}（hex）并输出警告（《通用功能说明》§2.3）。</p>
 */
@Slf4j
@Component
public class AuthSecret {

    private final BifrostProperties properties;
    private final Environment environment;

    @Getter
    private SecretKey key;

    public AuthSecret(BifrostProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void load() {
        String envSecret = environment.getProperty("BIFROST_AUTH_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            this.key = deriveKey(envSecret);
            return;
        }
        Path secretFile = secretFile();
        try {
            if (Files.isRegularFile(secretFile)) {
                this.key = new SecretKeySpec(SubsonicTokenUtil.unhex(Files.readString(secretFile).trim()), "AES");
                return;
            }
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            Files.createDirectories(secretFile.getParent());
            Files.writeString(secretFile, SubsonicTokenUtil.hex(random));
            this.key = new SecretKeySpec(random, "AES");
            log.warn("未配置 BIFROST_AUTH_SECRET，已自动生成主密钥并写入 {}（生产环境应显式配置）", secretFile);
        } catch (Exception e) {
            throw new IllegalStateException("初始化认证主密钥失败: " + secretFile, e);
        }
    }

    private Path secretFile() {
        Path dbParent = properties.getDb().getPath().toAbsolutePath().normalize().getParent();
        return dbParent.resolve("secret.key");
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("派生 AES 密钥失败", e);
        }
    }
}
