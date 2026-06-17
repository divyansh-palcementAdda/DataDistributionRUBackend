package com.app.datadistribution.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.datadistribution.Model.User;
import com.app.datadistribution.repository.IUserRepository;

/**
 * Service implementation for Spring Security to load user-specific data for
 * authentication.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

	private final IUserRepository userRepository;

	@Autowired
	public UserDetailsServiceImpl(IUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Loads a user by their username OR email.
	 *
	 * @param usernameOrEmail the username or email of the user
	 * @return UserDetails object containing authentication info
	 * @throws UsernameNotFoundException if user with given identifier does not
	 *                                   exist
	 */
	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(usernameOrEmail).or(() -> userRepository.findByUsername(usernameOrEmail)) // ✅
				// support
				// username
				// also
				.orElseThrow(() -> {
					logger.error("User not found with username/email: {}", usernameOrEmail);
					return new UsernameNotFoundException("User not found with: " + usernameOrEmail);
				});

		logger.info("User found with username/email: {}", usernameOrEmail);
		return UserDetailsImpl.build(user);
	}
}
