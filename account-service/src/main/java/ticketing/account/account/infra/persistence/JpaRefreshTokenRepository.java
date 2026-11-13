package ticketing.account.account.infra.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ticketing.account.account.domain.model.RefreshToken;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT r
			FROM RefreshToken r
			WHERE r.tokenHash = :tokenHash
			"""
	)
	Optional<RefreshToken> findByTokenHashForUpdate(
			@Param("tokenHash") String tokenHash
	);
	
	Optional<RefreshToken> findByTokenHash(String tokenHash);
	
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(
			"""
			UPDATE RefreshToken r
			SET r.revokedAt = :now
			WHERE r.sessionId = :sessionId
				AND r.revokedAt IS NULL
			"""
	)
	int revokeBySessionId(
			@Param("sessionId") UUID sessionId,
			@Param("now") Instant now
	);
	
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(
			"""
			UPDATE RefreshToken r
			SET r.revokedAt = :now
			WHERE r.accountId = :accountId
				AND r.revokedAt IS NULL
			"""
	)
	int revokeByAccountId(
			@Param("accountId") UUID accountId,
			@Param("now") Instant now
	);
}
