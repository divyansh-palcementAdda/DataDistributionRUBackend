package com.app.datadistribution.payload;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class UserRequest {
	
	@NotBlank(message = "Username is required")
	@Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
	@Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, digits, dots, underscores, and hyphens")
	private String username;

	@NotBlank(message = "Password is required")
	@Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
	private String password;

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Size(max = 200)
	private String email;

	@NotBlank(message = "Full name is required")
	@Size(min = 2, max = 80, message = "Full name must be between 2 and 80 characters")
	@Pattern(regexp = "^[a-zA-Z ]+$", message = "Full name can only contain alphabets and spaces")
	private String fullName;

	@Pattern(regexp = "^(\\+?[0-9]{10,15})?$", message = "Invalid mobile number format")
	private String mobile;

	@NotEmpty(message = "At least one role is required")
	private Set<String> roles;

	private String otp;

	private Set<Long> consultancyIds;
}
