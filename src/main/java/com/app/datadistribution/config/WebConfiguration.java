package com.app.datadistribution.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfiguration {

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();

//        config.setAllowedOrigins(List.of(
//                "http://localhost:4200",
//                "http://192.168.0.197:5173",
//                "http://localhost:5173",
//                "http://192.168.0.177:5173",
//                "http://172.29.176.1:4200",
//                "https://LiveProjectDomian.com",
//                "http://192.168.0.177:4200"
//        ));
		config.setAllowedOriginPatterns(List.of("*"));

		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

		config.setAllowedHeaders(List.of("*"));
		config.setExposedHeaders(List.of("Authorization"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		// Dedicated CORS configuration for reporting endpoints
		CorsConfiguration reportingConfig = new CorsConfiguration();
		reportingConfig.setAllowedOrigins(List.of(
		        "http://localhost:4300",
		        "https://dev.areyoureporting.com",
		        "https://areyoureporting.com"
		));
		reportingConfig.setAllowedMethods(List.of("GET", "OPTIONS"));
		reportingConfig.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-API-KEY"));
		reportingConfig.setExposedHeaders(List.of("Authorization"));
		reportingConfig.setAllowCredentials(true);
		reportingConfig.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/students/reporting/**", reportingConfig);
		source.registerCorsConfiguration("/**", config);

		return source;
	}
}
