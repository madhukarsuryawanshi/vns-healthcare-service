package com.vns.healthcare.entity;

import com.vns.healthcare.security.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class AuditEntityListenerRegistrar {

    public AuditEntityListenerRegistrar(UserRepository userRepository) {
        AuditEntityListener.setUserRepository(userRepository);
    }
}
