package com.app.datadistribution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.app.datadistribution.Model.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	Optional<RefreshToken> findByRefreshToken(String token);

	void deleteByRefreshToken(String token);

	Optional<RefreshToken> findByUser_UserIdAndAccessToken(Long userId, String accessToken);

	List<RefreshToken> findByUser_UserId(Long userId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.userId = :userId")
	int revokeAllByUserId(@Param("userId") Long userId);

	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM RefreshToken rt WHERE rt.user.userId = :userId")
	int deleteByUser_UserId(@Param("userId") Long userId);

	@Modifying(clearAutomatically = true)
	@Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < CURRENT_TIMESTAMP")
	int deleteExpiredTokens();
}
