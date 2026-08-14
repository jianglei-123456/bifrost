package com.bifrost.core.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 口令加密组件（AES-GCM，随机 IV 前置密文）。
 *
 * <p>存储格式：hex(IV[12] || ciphertext)。因 Subsonic 令牌验证需还原明文（md5(password+salt)），
 * 口令必须可逆加密（《音乐管理技术设计》§9、《Subsonic_API_参考》§1.5）。</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final AuthSecret authSecret;

    /** 加密：hex(iv + ciphertext)。 */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, authSecret.getKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return SubsonicTokenUtil.hex(combined);
        } catch (Exception e) {
            throw new IllegalStateException("口令加密失败", e);
        }
    }

    /** 解密；口令格式非法或密钥不符抛异常（调用方视为认证失败）。 */
    public String decrypt(String stored) {
        try {
            byte[] combined = SubsonicTokenUtil.unhex(stored);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, authSecret.getKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("口令解密失败", e);
        }
    }
}
