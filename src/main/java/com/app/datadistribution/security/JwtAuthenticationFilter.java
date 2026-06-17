// JwtAuthenticationFilter.java
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

import com.app.datadistribution.service.impl.RefreshTokenService;

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

	private final JwtProvider jwtProvider;
	private final UserDetailsServiceImpl userDetailsService;
	private final RefreshTokenService refreshTokenService;

	private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
			"/api/auth/logout", "/api/auth/logout-all", "/api/auth/verify-otp", "/api/auth/resend-otp", "/v3/api-docs",
			"/swagger-ui", "/swagger-ui.html", "/webjars", "/swagger-resources");

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                               HttpServletResponse response,
	                               FilterChain filterChain)
	        throws ServletException, IOException {

	    String path = request.getServletPath();

	    // ✅ Skip public endpoints
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

	        if (jwtProvider.isTokenValid(jwt)) {

	            // 🔥🔥🔥 CRITICAL FIX → CHECK TOKEN TYPE
	            String type = jwtProvider.extractClaim(jwt, claims -> claims.get("type", String.class));

	            // ✅ If API CLIENT → skip this filter
	            if ("API_CLIENT".equals(type)) {
	                filterChain.doFilter(request, response);
	                return;
	            }

	            // ✅ USER FLOW ONLY BELOW
	            String username = jwtProvider.extractSubject(jwt);

	            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

	                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

	                // Verify that user is active and email verified (isEnabled)
	                if (userDetails.isEnabled()) {
	                    // Check tokenVersion claim
	                    Long tokenVersionFromJwt = null;
	                    try {
	                        tokenVersionFromJwt = jwtProvider.extractClaim(jwt, claims -> {
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
		return path.startsWith("/api/auth") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
				|| path.startsWith("/swagger-ui.html") || path.startsWith("/webjars")
				|| path.startsWith("/swagger-resources");
	}
}