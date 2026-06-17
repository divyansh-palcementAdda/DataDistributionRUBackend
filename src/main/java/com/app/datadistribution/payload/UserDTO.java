package com.app.datadistribution.payload;

import java.util.Set;

import com.app.datadistribution.Model.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

	private Long userId;

	private String username;

	private String email;

	private String fullName;

	private String mobile; // ✅ added (exists in entity)

	private Set<String> roles; // ✅ mapped from Role entity

	private UserStatus status;

	private Boolean emailVerified; // ✅ use wrapper (better for null safety)

	private String pendingEmail;

	// ✅ Audit fields (optional but useful)
	private String createdAt;
	private String updatedAt;

	
}