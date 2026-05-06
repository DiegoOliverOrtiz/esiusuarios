package esi.edu.usuarios.usuarios.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Value("${app.security.headers.content-security-policy:default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'}")
    private String contentSecurityPolicy;

    @Value("${app.security.headers.referrer-policy:no-referrer}")
    private String referrerPolicy;

    @Value("${app.security.headers.hsts:max-age=31536000; includeSubDomains}")
    private String hsts;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        addSecurityHeaders(response);
        filterChain.doFilter(request, response);
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", referrerPolicy);
        response.setHeader("Strict-Transport-Security", hsts);
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
    }
}
