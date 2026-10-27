package com.vns.healthcare.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private NoCacheFilter noCacheFilter;

    @Mock
    private UserActivityService userActivityService;

    private SecurityConfig config;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig(userDetailsService, noCacheFilter, userActivityService);
    }

    @Test
    void passwordEncoder_shouldCreateBCryptEncoder() {
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder.matches("secret", encoder.encode("secret")));
    }

    @Test
    void authenticationProvider_shouldBeConfigured() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        DaoAuthenticationProvider provider = config.authenticationProvider(encoder);
        assertNotNull(provider);
        assertTrue(provider != null);
    }

    @Test
    void accessDeniedHandler_shouldNotBeNull() {
        assertNotNull(config.accessDeniedHandler());
    }
}
