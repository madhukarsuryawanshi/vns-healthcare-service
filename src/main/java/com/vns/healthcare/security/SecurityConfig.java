package com.vns.healthcare.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UserDetailsServiceImpl userDetailsService;
    private final NoCacheFilter noCacheFilter;
    private final UserActivityService userActivityService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          NoCacheFilter noCacheFilter,
                          UserActivityService userActivityService) {
        this.userDetailsService = userDetailsService;
        this.noCacheFilter = noCacheFilter;
        this.userActivityService = userActivityService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
            String uri = request.getRequestURI();
            String message = "You do not have access, kindly contact your Admin";
            request.getSession().setAttribute("accessDeniedMessage", message);
            log.warn("Access denied for user [{}] on URI [{}]", user, uri);

            String referer = request.getHeader("Referer");
            String targetUrl = (referer != null && !referer.trim().isEmpty()) ? referer : request.getContextPath() + "/";
            response.sendRedirect(targetUrl);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.addFilterAfter(noCacheFilter, UsernamePasswordAuthenticationFilter.class);

        http
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-password", "/reset-password", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                           userActivityService.log(authentication.getName(), "LOGIN", "SESSION", null,
                                   "User logged in to the system", request.getRemoteAddr());
                           response.sendRedirect(request.getContextPath() + "/");
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) -> {
                           if (authentication != null) {
                               userActivityService.log(authentication.getName(), "LOGOUT", "SESSION", null,
                                       "User logged out of the system", request.getRemoteAddr());
                           }
                           response.sendRedirect(request.getContextPath() + "/login?logout");
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());

        return http.build();
    }
}

