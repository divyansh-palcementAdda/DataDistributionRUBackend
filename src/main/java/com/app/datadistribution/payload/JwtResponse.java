package com.app.datadistribution.payload;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
public class JwtResponse {
	private String accessToken;
	private String refreshToken;
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
	private Set<String> roles = new HashSet<>();

 
}