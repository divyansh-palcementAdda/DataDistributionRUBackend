package com.app.datadistribution.security;

import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip public endpoints
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = extractJwtFromRequest(request);

        if (!StringUtils.hasText(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtService.isTokenValid(jwt)) {
                String type = null;
                try {
                    type = jwtService.extractClaim(jwt, claims -> claims.get("type", String.class));
                } catch (Exception e) {
                    // ignore if claim doesn't exist
                }

                // If API CLIENT → skip this filter to handle in subsequent filters or interceptors
                if ("API_CLIENT".equals(type)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtService.extractSubject(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (userDetails.isEnabled()) {
                        Long tokenVersionFromJwt = null;
                        try {
                            tokenVersionFromJwt = jwtService.extractClaim(jwt, claims -> {
                                Object verObj = claims.get("tokenVersion");
                                if (verObj instanceof Number) {
                                    return ((Number) verObj).longValue();
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            log.warn("Failed to extract token version from JWT: {}", e.getMessage());
                        }

                        if (userDetails instanceof UserDetailsImpl && 
                            (tokenVersionFromJwt == null || !tokenVersionFromJwt.equals(((UserDetailsImpl) userDetails).getTokenVersion()))) {
                            log.warn("JWT request rejected: token version mismatch for user {}. Token version: {}, User version: {}", 
                                     username, tokenVersionFromJwt, ((UserDetailsImpl) userDetails).getTokenVersion());
                        } else {
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            userDetails.getAuthorities()
                                    );

                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request)
                            );

                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    } else {
                        log.warn("JWT request rejected: user {} is not active or email not verified", username);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT processing failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/auth/register") 
                || path.equals("/api/auth/login") 
                || path.equals("/api/auth/refresh-token") 
                || path.equals("/api/auth/logout")
                || path.startsWith("/v3/api-docs") 
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html") 
                || path.startsWith("/webjars")
                || path.startsWith("/swagger-resources");
    }
}