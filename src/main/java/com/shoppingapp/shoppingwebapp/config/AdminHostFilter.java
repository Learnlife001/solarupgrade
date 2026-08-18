package com.shoppingapp.shoppingwebapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Sends the front page of the admin hostname to the admin dashboard.
 *
 * <p>One application serves both the shop and the back office, so a separate
 * hostname is a front door rather than a separate deployment. Someone opening
 * the admin address expects the dashboard, not the catalogue; this is the whole
 * of that difference.
 *
 * <p>Only the root path is redirected. Sign-in, the stylesheet and the scripts
 * have to keep working on that hostname, and a rule that rewrote everything
 * would break them. The hostname is not a security boundary either way —
 * {@code ROLE_ADMIN} is, and it applies on whichever address the request
 * arrives at.
 *
 * <p>Off unless {@code app.admin.host} is set, so nothing changes until a
 * hostname actually points here.
 */
@Component
public class AdminHostFilter extends OncePerRequestFilter {

    private final String adminHost;

    public AdminHostFilter(@Value("${app.admin.host:}") String adminHost) {
        this.adminHost = adminHost == null ? "" : adminHost.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return adminHost.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("/".equals(request.getRequestURI()) && matchesAdminHost(request)) {
            response.sendRedirect("/admin");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Compared without the port, because the Host header carries one in
     * development and not in production, and a rule that only worked in one of
     * those would be found the hard way.
     */
    private boolean matchesAdminHost(HttpServletRequest request) {
        String host = request.getServerName();
        return host != null && host.toLowerCase(Locale.ROOT).equals(adminHost);
    }
}
