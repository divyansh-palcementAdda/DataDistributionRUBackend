package com.app.datadistribution.payload;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserUpdateRequest {

	@Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
	@Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, digits, dots, underscores, and hyphens")
	private String username;


	private String password;

	@Email(message = "Invalid email format")
	@Size(max = 200)
	private String email;

	@Size(min = 2, max = 80, message = "Full name must be between 2 and 80 characters")
	@Pattern(regexp = "^[a-zA-Z ]+$", message = "Full name can only contain alphabets and spaces")
	private String fullName;

	@Pattern(regexp = "^(\\+?[0-9]{10,15})?$", message = "Invalid mobile number format")
	private String mobile;

	private Set<String> roles;

	private String otp;

	private Set<Long> consultancyIds;
}
