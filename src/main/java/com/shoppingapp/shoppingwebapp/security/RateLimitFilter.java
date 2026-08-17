package com.shoppingapp.shoppingwebapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caps how often one caller can hit the endpoints worth attacking.
 *
 * <p>Only POSTs are limited. Reading the sign-in page costs nothing; submitting
 * it is what guesses a password, and what sends an email.
 *
 * <p>Callers are identified by {@code getRemoteAddr()} rather than by reading
 * {@code X-Forwarded-For} here. That header is trivially forged, so parsing it
 * directly would let an attacker mint a fresh identity per request and defeat
 * the whole filter. Instead {@code server.forward-headers-strategy=framework}
 * makes the container resolve the real client address from the proxy it trusts,
 * and this filter reads the result. The two settings only work together: drop
 * that property and every request behind Render's proxy looks like one client.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Ordered because the first match wins.
     *
     * <p>The email-sending paths are tightest: an unlimited resend loop costs
     * real money on the mail provider and can get the sending domain marked as
     * a spam source, which is far more expensive to undo than a locked-out
     * customer waiting a few minutes.
     */
    private static final Map<String, RateLimiter.Policy> POLICIES = new LinkedHashMap<>();

    static {
        POLICIES.put("/resend-verification", new RateLimiter.Policy(3, Duration.ofHours(1)));
        POLICIES.put("/register", new RateLimiter.Policy(5, Duration.ofHours(1)));
        POLICIES.put("/verify", new RateLimiter.Policy(15, Duration.ofMinutes(15)));
        POLICIES.put("/login", new RateLimiter.Policy(10, Duration.ofMinutes(15)));
    }

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimiter.Policy policy = policyFor(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        if (rateLimiter.tryAcquire(key, policy)) {
            chain.doFilter(request, response);
            return;
        }

        Duration retryAfter = rateLimiter.retryAfter(key, policy);
        log.warn("Rate limit reached for {} from {}; refusing for another {}s",
                request.getRequestURI(), request.getRemoteAddr(), retryAfter.toSeconds());
        respondTooMany(response, retryAfter);
    }

    private static RateLimiter.Policy policyFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        for (Map.Entry<String, RateLimiter.Policy> entry : POLICIES.entrySet()) {
            if (path.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * A plain page rather than a redirect with a flash message, because at this
     * point the request has not reached Spring MVC and there is no session to
     * put a message in. Retry-After is set so anything automated is told when
     * to come back rather than hammering.
     */
    private static void respondTooMany(HttpServletResponse response, Duration retryAfter) throws IOException {
        long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
        // 429 has no constant in the servlet API; Spring's enum does.
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter.toSeconds())));
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("""
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Too many attempts · SolarUpgrade</title>
                <link rel="stylesheet" href="/css/style.css"></head>
                <body><main class="container narrow">
                <h1>Too many attempts</h1>
                <p>For safety we have paused this for about %d minute(s). Nothing is wrong
                with your account &mdash; please try again shortly.</p>
                <p class="mt-sm"><a href="/products">Back to the shop</a></p>
                </main></body></html>
                """.formatted(minutes));
    }
}
