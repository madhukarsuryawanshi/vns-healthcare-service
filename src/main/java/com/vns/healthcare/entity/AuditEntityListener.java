package com.vns.healthcare.entity;

import com.vns.healthcare.security.AppUser;
import com.vns.healthcare.security.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

public class AuditEntityListener {

    public static void setUserRepository(UserRepository userRepository) {
        // intentionally no-op: do not resolve the current user from a JPA listener
        // because repository access here re-enters the persistence context.
    }

    @PrePersist
    @PreUpdate
    public void setAuditFields(Object target) {
        if (!(target instanceof AuditableEntity)) {
            return;
        }
        AuditableEntity auditable = (AuditableEntity) target;
        String currentUsername = currentUsername();
        if (auditable.getCreatedBy() == null || auditable.getCreatedBy().trim().isEmpty()) {
            auditable.setCreatedBy(currentUsername);
        }
        auditable.setUpdatedBy(currentUsername);
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser) {
            return ((AppUser) principal).getUsername();
        }
        if (principal instanceof com.vns.healthcare.security.AuthenticatedUser) {
            return ((com.vns.healthcare.security.AuthenticatedUser) principal).getUsername();
        }
        if (principal instanceof String) {
            return (String) principal;
        }

        return authentication.getName();
    }
}
