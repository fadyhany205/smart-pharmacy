package com.pharmacy.config;

import com.pharmacy.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/webjars/**", "/css/**", "/js/**", "/img/**", "/uploads/**", "/error").permitAll()
                .requestMatchers("/settings/**", "/users/**").hasRole("ADMIN")
                // Everyone may look at expiry / batches (cashiers check before selling); changes are manager-only below.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/inventory/expiry", "/inventory/batches").authenticated()
                .requestMatchers("/reports/**", "/purchases/**", "/api/purchases/**", "/api/suppliers/**", "/suppliers/**",
                        "/inventory/**", "/categories/**", "/units/**", "/returns/purchase/**",
                        "/products/new", "/products/*/edit", "/products/*/delete", "/products/save",
                        "/products/import", "/products/import/**")
                    .hasAnyRole("ADMIN", "MANAGER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            .rememberMe(rm -> rm.key("pharmacy-system-remember-me").tokenValiditySeconds(14 * 24 * 3600));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
                .filter(u -> u.isActive())
                .map(u -> org.springframework.security.core.userdetails.User
                        .withUsername(u.getUsername())
                        .password(u.getPassword())
                        .roles(u.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
