package com.app.datadistribution.security;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

public class JwtUtils {
	private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
	        Decoders.BASE64.decode("your-256-bit-base64-secret-here-very-long-and-secure")
	    );

	    public static String extractJti(String token) {
	        return Jwts.parserBuilder()
	            .setSigningKey(SECRET_KEY)
	            .build()
	            .parseClaimsJws(token)
	            .getBody()
	            .get("jti", String.class); // assuming you set jti claim
}
}
