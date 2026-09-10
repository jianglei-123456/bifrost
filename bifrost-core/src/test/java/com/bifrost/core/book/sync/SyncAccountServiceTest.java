package com.bifrost.core.book.sync;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.security.PasswordCipher;
import com.bifrost.core.security.SubsonicTokenUtil;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.repo.SyncAccountRepository;
import com.bifrost.domain.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同步账号服务测试（M3-sync T1.4）：口令校验、口令生成、R2c 强制不同。
 */
class SyncAccountServiceTest {

    private final SyncAccountRepository accountRepository = mock(SyncAccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordCipher passwordCipher = mock(PasswordCipher.class);
    private final BifrostProperties properties = new BifrostProperties();

    private final SyncAccountService service =
            new SyncAccountService(accountRepository, userRepository, passwordCipher, properties);

    private SyncAccount account(String username, String encrypted) {
        SyncAccount account = new SyncAccount();
        account.setId(1L);
        account.setUsername(username);
        account.setEncryptedPassword(encrypted);
        return account;
    }

    private void givenAdminPassword(String username, String encrypted, String plain) {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername(username);
        admin.setEncryptedPassword(encrypted);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordCipher.decrypt(encrypted)).thenReturn(plain);
    }

    @Test
    void verifyAcceptsMd5OfPassword() {
        when(accountRepository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "enc-secret")));
        when(passwordCipher.decrypt("enc-secret")).thenReturn("secret");

        assertThat(service.verify("reader", md5("secret"))).isTrue();
        // 大小写不敏感（客户端发的是小写 hex）
        assertThat(service.verify("reader", md5("secret").toUpperCase())).isTrue();
    }

    @Test
    void verifyRejectsWrongKeyUnknownUserAndBlankInput() {
        when(accountRepository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "enc-secret")));
        when(passwordCipher.decrypt("enc-secret")).thenReturn("secret");

        assertThat(service.verify("reader", md5("wrong"))).isFalse();
        assertThat(service.verify("someone-else", md5("secret"))).isFalse();
        assertThat(service.verify(null, md5("secret"))).isFalse();
        assertThat(service.verify("reader", "  ")).isFalse();
    }

    @Test
    void verifyReturnsFalseWhenStoredPasswordCannotBeDecrypted() {
        when(accountRepository.findByUsername("reader")).thenReturn(Optional.of(account("reader", "broken")));
        when(passwordCipher.decrypt("broken")).thenThrow(new IllegalStateException("口令解密失败"));

        assertThat(service.verify("reader", md5("secret"))).isFalse();
    }

    @Test
    void currentOrCreateCreatesDefaultAccountWithRandomPassword() {
        when(accountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(passwordCipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(accountRepository.save(any(SyncAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncAccount created = service.currentOrCreate();

        assertThat(created.getUsername()).isEqualTo(SyncAccountService.DEFAULT_USERNAME);
        ArgumentCaptor<String> generated = ArgumentCaptor.forClass(String.class);
        verify(passwordCipher).encrypt(generated.capture());
        assertThat(generated.getValue()).hasSize(16).doesNotContainAnyWhitespaces();
        assertThat(generated.getValue()).matches("[a-zA-Z2-9]+");
    }

    @Test
    void updateRejectsSyncPasswordEqualToAdminPassword() {
        when(accountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(account("reader", "enc-secret")));
        givenAdminPassword("admin", "enc-admin", "testpass");

        assertThatThrownBy(() -> service.update(null, "testpass"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能与管理员口令相同");
        verify(accountRepository, never()).save(any(SyncAccount.class));
    }

    @Test
    void updateAcceptsDifferentPasswordAndEncryptsIt() {
        SyncAccount existing = account("reader", "enc-secret");
        when(accountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        givenAdminPassword("admin", "enc-admin", "testpass");
        when(passwordCipher.encrypt("synctest")).thenReturn("enc-synctest");
        when(accountRepository.save(any(SyncAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncAccount updated = service.update("  reader2  ", "synctest");

        assertThat(updated.getUsername()).isEqualTo("reader2");
        assertThat(updated.getEncryptedPassword()).isEqualTo("enc-synctest");
    }

    @Test
    void updateRejectsIllegalUsername() {
        when(accountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(account("reader", "enc-secret")));

        assertThatThrownBy(() -> service.update("bad:name", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("':'");
        assertThatThrownBy(() -> service.update("x".repeat(65), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("64");
        assertThatThrownBy(() -> service.update("   ", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void revealPasswordReturnsPlaintextAndNullOnFailure() {
        assertThat(service.revealPassword(account("reader", "enc-secret"))).isNull();
        when(passwordCipher.decrypt("enc-secret")).thenReturn("secret");
        assertThat(service.revealPassword(account("reader", "enc-secret"))).isEqualTo("secret");
    }

    @Test
    void resetPasswordStoresFreshEncryptedPassword() {
        when(accountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(account("reader", "enc-old")));
        when(passwordCipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(accountRepository.save(any(SyncAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncAccount account = service.resetPassword();

        assertThat(account.getEncryptedPassword()).startsWith("enc:").isNotEqualTo("enc-old");
    }

    @Test
    void generatePasswordAvoidsConfusableCharacters() {
        for (int i = 0; i < 20; i++) {
            String password = service.generatePassword();
            assertThat(password).hasSize(16).doesNotContain("0", "O", "1", "l", "I");
        }
    }

    private static String md5(String value) {
        return SubsonicTokenUtil.token(value, "");
    }
}
