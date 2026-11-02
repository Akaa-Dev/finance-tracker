package com.financetracker.auth;
import com.financetracker.common.ApiProblems;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Locale;
@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(OwnerRepository owners) {
        return username -> owners.findByEmail(username.strip().toLowerCase(Locale.ROOT))
                .map(owner -> User.withUsername(owner.getEmail()).password(owner.getPasswordHash()).roles("OWNER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                    .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((req,res,ex) -> ApiProblems.write(res,401))
                    .accessDeniedHandler((req,res,ex) -> ApiProblems.write(res,403)))
                .formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
                    .successHandler((req,res,auth) -> res.setStatus(204))
                    .failureHandler((req,res,ex) -> ApiProblems.write(res,401)))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                    .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .build();
    }
}
