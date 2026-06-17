package com.app.datadistribution.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@Component
public class UserSecurityUtil {

	public Long getCurrentUserId() {
	    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
	    if (auth == null || !auth.isAuthenticated()) {
	        return null;
	    }

	    Object principal = auth.getPrincipal();

	    if (principal instanceof User) {
	        // If you're using Spring's default UserDetails
	        return null; // no ID stored, only username
	    }

	    if (principal instanceof UserDetailsImpl userDetails) {
	        return userDetails.getId();
	    }

	    return null;
	}

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public boolean hasAdminRole() {
        return hasRole("ADMIN");
    }

    public boolean hasHodRole() {
        return hasRole("HOD");
    }
}
