package com.shoppingapp.shoppingwebapp.config;

import com.shoppingapp.shoppingwebapp.repository.UserRepository;
import com.shoppingapp.shoppingwebapp.security.LoginAttemptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
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
    public UserDetailsService userDetailsService(UserRepository userRepository,
                                                LoginAttemptService loginAttempts) {
        return email -> userRepository.findByEmail(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .disabled(!user.isEmailVerified())
                        // Spring Security's own flag, so the cooldown is
                        // enforced before the password is even compared and
                        // arrives as a LockedException rather than needing a
                        // check bolted on somewhere later.
                        .accountLocked(loginAttempts.isLocked(email))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    /**
     * Sends an unverified sign-in attempt to a page that explains the problem
     * and offers a fresh code, rather than the generic "wrong password", and a
     * locked one to a page that says to wait.
     *
     * <p>Only a genuinely wrong password counts towards a lock. An unverified
     * account is refused for an unrelated reason, and counting it would let a
     * new customer who has not checked their inbox lock themselves out by
     * trying twice.
     */
    private AuthenticationFailureHandler authenticationFailureHandler(LoginAttemptService loginAttempts) {
        return (request, response, exception) -> {
            String target;
            if (exception instanceof DisabledException) {
                target = "/login?unverified";
            } else if (exception instanceof LockedException) {
                target = "/login?locked";
            } else {
                loginAttempts.recordFailure(request.getParameter("username"));
                target = loginAttempts.isLocked(request.getParameter("username"))
                        ? "/login?locked"
                        : "/login?error";
            }
            new SimpleUrlAuthenticationFailureHandler(target).onAuthenticationFailure(request, response, exception);
        };
    }

    /** Clears the failure count, so earlier typos do not follow a customer around. */
    private AuthenticationSuccessHandler authenticationSuccessHandler(LoginAttemptService loginAttempts) {
        SimpleUrlAuthenticationSuccessHandler delegate = new SimpleUrlAuthenticationSuccessHandler("/products");
        return (request, response, authentication) -> {
            loginAttempts.recordSuccess(authentication.getName());
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          LoginAttemptService loginAttempts) throws Exception {
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
                        .successHandler(authenticationSuccessHandler(loginAttempts))
                        .failureHandler(authenticationFailureHandler(loginAttempts))
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
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        // Only emitted on requests the container considers
                        // secure, which behind Render's proxy depends on
                        // server.forward-headers-strategy being set. The two
                        // go together.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // Everything this app loads is its own. No inline
                        // styles or scripts remain, so nothing here needs
                        // 'unsafe-inline' -- which is the concession that
                        // usually makes a CSP decorative.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "img-src 'self' data:",
                                "style-src 'self'",
                                "script-src 'self'",
                                "connect-src 'self'",
                                "form-action 'self'",
                                "frame-ancestors 'self'",
                                "base-uri 'self'",
                                "object-src 'none'"))));

        return http.build();
    }
}
