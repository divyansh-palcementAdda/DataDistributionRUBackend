package com.app.datadistribution.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.app.datadistribution.security.JwtAuthenticationFilter;
import com.app.datadistribution.security.UserDetailsServiceImpl;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfiguration {

	private final UserDetailsServiceImpl userDetailsService;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfiguration(UserDetailsServiceImpl userDetailsService,
			JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.userDetailsService = userDetailsService;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;

	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.cors(cors -> {
		}).csrf(csrf -> csrf.disable())
		.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.authorizeHttpRequests(auth -> auth

				// ✅ PUBLIC ENDPOINTS
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",

						"/api/auth/**", // user auth
						"/api/auth/api-clients/**", // ✅ API client token endpoint
						"/api/students/reporting/**" // ✅ Integration endpoint
						).permitAll()

				// ✅ API CLIENT ACCESS (ONLY THIS API)
				.requestMatchers("/api/students/website-admission").authenticated()

				// ✅ ROLE-BASED USER APIS
				.requestMatchers("/api/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUB_ADMIN")
				.requestMatchers("/api/tasks/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUB_ADMIN")
				.requestMatchers("/api/reports/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUB_ADMIN")

				.anyRequest().authenticated())

		// 🔥 ORDER MATTERS
		// First check Integration API Key

		// Then normal USER JWT
		.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

		.exceptionHandling(ex -> ex.authenticationEntryPoint(
				(req, res, authEx) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
				.accessDeniedHandler((req, res, accessEx) -> res.sendError(HttpServletResponse.SC_FORBIDDEN,
						"Access Denied")));

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}
}