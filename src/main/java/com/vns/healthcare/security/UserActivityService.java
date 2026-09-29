package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserActivityRepository userActivityRepository;

    public UserActivityService(UserActivityRepository userActivityRepository) {
        this.userActivityRepository = userActivityRepository;
    }

    @Transactional
    public void log(String username, String activityType, String entityType, Long entityId, String description) {
        log(username, activityType, entityType, entityId, description, null);
    }

    @Transactional
    public void log(String username, String activityType, String entityType, Long entityId, String description, String ipAddress) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        UserActivityLog entry = new UserActivityLog();
        entry.setUsername(username.trim());
        entry.setActivityType(normalizeType(activityType));
        entry.setEntityType(entityType == null ? null : entityType.trim());
        entry.setEntityId(entityId);
        entry.setDescription(description == null ? "" : description.trim());
        entry.setIpAddress(ipAddress == null ? null : ipAddress.trim());
        userActivityRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<UserActivityLog> recentPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return userActivityRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<UserActivityLog> recent(int limit) {
        int safeLimit = Math.max(limit, 1);
        Pageable pageable = PageRequest.of(0, safeLimit);
        return userActivityRepository.findAllByOrderByCreatedAtDesc(pageable).getContent();
    }

    @Transactional(readOnly = true)
    public List<UserActivityLog> recentForUser(String username, int limit) {
        if (username == null || username.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        int safeLimit = Math.max(limit, 1);
        Pageable pageable = PageRequest.of(0, safeLimit);
        return userActivityRepository.findByUsernameOrderByCreatedAtDesc(username.trim(), pageable).getContent();
    }

    public void logCurrentUser(String activityType, String entityType, Long entityId, String description) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return;
        }
        String username = authentication.getName();
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        log(username, activityType, entityType, entityId, description);
    }

    private String normalizeType(String activityType) {
        if (activityType == null || activityType.trim().isEmpty()) {
            return "ACTION";
        }
        String normalized = activityType.trim().toUpperCase();
        switch (normalized) {
            case "CREATE":
            case "UPDATE":
            case "DELETE":
            case "LOGIN":
            case "LOGOUT":
            case "VIEW":
            case "PASSWORD_CHANGE":
                return normalized;
            default:
                return "ACTION";
        }
    }
}
