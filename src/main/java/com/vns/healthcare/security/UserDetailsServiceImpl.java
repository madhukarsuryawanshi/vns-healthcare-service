package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String trimmedUsername = username == null ? "" : username.trim();
        AppUser appUser = userRepository.findByUsername(trimmedUsername)
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found for username [{}]", trimmedUsername);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();
        for (Role role : appUser.getRoles()) {
            String normalizedRole = role.getName() == null ? "USER" : role.getName().trim().toUpperCase();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
            for (String permission : role.getPermissions()) {
                if (permission != null && !permission.trim().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(permission.trim().toLowerCase()));
                }
            }
        }
        for (String permission : appUser.getPermissions()) {
            if (permission != null && !permission.trim().isEmpty()) {
                authorities.add(new SimpleGrantedAuthority(permission.trim().toLowerCase()));
            }
        }

        log.info("Loaded user [{}] with {} authorities", appUser.getUsername(), authorities.size());
        return new User(
                appUser.getUsername(),
                appUser.getPassword(),
                appUser.isEnabled(),
                true,
                true,
                true,
                authorities);
    }
}
