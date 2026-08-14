package com.bifrost.domain.repo;

import com.bifrost.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户仓储。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按用户名查询（唯一） */
    Optional<User> findByUsername(String username);
}
