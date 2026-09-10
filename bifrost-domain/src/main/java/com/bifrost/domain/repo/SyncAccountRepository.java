package com.bifrost.domain.repo;

import com.bifrost.domain.entity.SyncAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 同步账号仓储（M3-sync T1.1）。
 */
public interface SyncAccountRepository extends JpaRepository<SyncAccount, Long> {

    /** 按用户名查询（唯一） */
    Optional<SyncAccount> findByUsername(String username);

    /** Day-one 单账号：取第一行 */
    Optional<SyncAccount> findFirstByOrderByIdAsc();
}
