package com.bifrost.domain.repo;

import com.bifrost.domain.entity.SyncDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 同步设备仓储（M3-sync T1.1，R6）。
 */
public interface SyncDeviceRepository extends JpaRepository<SyncDevice, Long> {

    /** 按账号 + 设备 ID 查询（唯一） */
    Optional<SyncDevice> findBySyncAccountIdAndDeviceId(Long syncAccountId, String deviceId);

    /** 设备列表（最近见到优先） */
    List<SyncDevice> findBySyncAccountIdOrderByLastSeenAtDesc(Long syncAccountId);

    long countBySyncAccountId(Long syncAccountId);
}
