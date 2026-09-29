package com.vns.healthcare.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivityLog, Long> {

    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<UserActivityLog> findAllByOrderByCreatedAtDesc();

    Page<UserActivityLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
}
