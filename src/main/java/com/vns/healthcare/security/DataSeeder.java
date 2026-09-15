package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(RoleRepository roleRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting application data seeding");
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> createRole("ADMIN", "Full system access"));

        Set<String> adminPermissions = new HashSet<String>(Arrays.asList(
                "dashboard",
                "employees",
                "customers",
                "attendance",
                "salary",
                "admin"));
        adminRole.setPermissions(adminPermissions);
        roleRepository.save(adminRole);

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> createRole("USER", "View-only access"));
        Set<String> userPermissions = new HashSet<String>(Arrays.asList(
                "dashboard",
                "employees"));
        userRole.setPermissions(userPermissions);
        roleRepository.save(userRole);

        if (!userRepository.existsByUsername("admin")) {
            AppUser adminUser = new AppUser();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("admin123"));
            adminUser.setEnabled(true);
            adminUser.setRoles(new HashSet<Role>(Arrays.asList(adminRole)));
            userRepository.save(adminUser);
            log.info("Created default admin user: admin");
        } else {
            log.info("Default admin user already exists");
        }
        log.info("Application data seeding completed");
    }

    private Role createRole(String name, String description) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        Role saved = roleRepository.save(role);
        log.info("Created role: {}", name);
        return saved;
    }
}
