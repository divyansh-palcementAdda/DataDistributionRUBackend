package com.app.datadistribution.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.app.datadistribution.Model.User;
import com.app.datadistribution.Model.UserStatus;

public interface IUserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);

	Optional<User> findByUserId(Long userid);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	List<User> findByStatus(UserStatus status);

	List<User> findByStatusOrderByFullNameAsc(UserStatus status);

	@Query(value = """
			    SELECT u.*
			    FROM users u
			    LEFT JOIN consultancy_representatives cr
			        ON u.user_id = cr.representative_id
			    WHERE cr.representative_id IS NULL
			""", nativeQuery = true)
	List<User> findUsersWithoutConsultancy();

	@Query(value = "SELECT u.* FROM users u LEFT JOIN consultancy_representatives cr ON u.user_id = cr.representative_id WHERE cr.representative_id IS NULL", countQuery = "SELECT COUNT(*) FROM users u LEFT JOIN consultancy_representatives cr ON u.user_id = cr.representative_id WHERE cr.representative_id IS NULL", nativeQuery = true)
	org.springframework.data.domain.Page<User> findUsersWithoutConsultancy(
			org.springframework.data.domain.Pageable pageable);

	Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);

	Optional<User> findByFullNameIgnoreCase(String val);
}
