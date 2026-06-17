package com.app.datadistribution.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.app.datadistribution.config.JwtConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

	private final JwtConfig jwtConfig;

	private Key getSigningKey() {
		byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
		return Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateAccessToken(String username, Map<String, Object> claims) {
		return Jwts.builder().setClaims(claims).setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getAccessTokenExpirationMs()))
				.signWith(getSigningKey(), SignatureAlgorithm.HS512).compact();
	}

	public String generateRefreshToken(String username, Map<String, Object> claims) {
		return Jwts.builder().setClaims(claims).setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshTokenExpirationMs()))
				.signWith(getSigningKey(), SignatureAlgorithm.HS512).compact();
	}

	public String extractSubject(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		Claims claims = extractAllClaims(token);
		return claimsResolver.apply(claims);
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
	}

	public boolean isTokenValid(String token) {
		try {
			extractAllClaims(token);
			return !isTokenExpired(token);
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	private boolean isTokenExpired(String token) {
		return extractAllClaims(token).getExpiration().before(new Date());
	}

	public static String maskToken(String token) {
		if (token == null || token.length() <= 8)
			return "****";
		return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
	}

	public String generateToken(String apiKey) {

	    return Jwts.builder()
	            .setSubject(apiKey)
	            .claim("type", "API_CLIENT") // ✅ VERY IMPORTANT
	            .setIssuedAt(new Date())
	            .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getAccessTokenExpirationMs()))
	            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
	            .compact();
	}

	public String extractType(String token) {
	    return extractClaim(token, claims -> claims.get("type", String.class));
	}
	
	public String extractApiKey(String token) {
	    return getClaims(token).getSubject();
	}
	
	public Claims getClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
	}
}