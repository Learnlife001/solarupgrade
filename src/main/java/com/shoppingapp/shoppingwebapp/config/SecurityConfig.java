package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Loads accounts from our own users table. Email is the username.
     *
     * <p>An unverified account is built as disabled, so Spring Security refuses
     * the login with a DisabledException before any password check matters.
     * That is what makes email verification actually gate access rather than
     * being decorative.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmail(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .disabled(!user.isEmailVerified())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    /**
     * Sends an unverified sign-in attempt to a page that explains the problem
     * and offers a fresh link, rather than the generic "wrong password".
     */
    private AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String target = (exception instanceof DisabledException) ? "/login?unverified" : "/login?error";
            new SimpleUrlAuthenticationFailureHandler(target).onAuthenticationFailure(request, response, exception);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Browsing the catalogue does not require an account.
                        .requestMatchers("/", "/products/**", "/register", "/login",
                                "/verify", "/verify/**", "/resend-verification",
                                "/css/**", "/js/**", "/images/**",
                                // Browsers fetch these before any session exists.
                                "/favicon.svg", "/favicon.ico",
                                "/h2-console/**").permitAll()
                        // Server-to-server, so no session ever exists here.
                        // Authenticity comes from PayPal's own signature check,
                        // not from being behind a login; see PaymentController.
                        .requestMatchers("/payments/*/webhook").permitAll()
                        // Only health and info are exposed; see application.properties.
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/products", false)
                        .failureHandler(authenticationFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/products")
                        .permitAll())
                // The H2 console renders in a frame and posts without a CSRF token.
                // Both exemptions are scoped to its path and it is only enabled
                // under the dev profile.
                // Webhooks are exempt because the caller is a payment
                // provider, which has no CSRF token and no session to forge;
                // the signature check is what stands in for both. Scoped to
                // the webhook paths only.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/payments/*/webhook"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
