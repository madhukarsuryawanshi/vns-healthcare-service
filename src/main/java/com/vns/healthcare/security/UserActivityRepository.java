package com.vns.healthcare.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivityLog, Long> {

    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UserActivityLog> findAllByOrderByCreatedAtDesc();

    Page<UserActivityLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    @Modifying
    @Query("DELETE FROM UserActivityLog u WHERE u.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
