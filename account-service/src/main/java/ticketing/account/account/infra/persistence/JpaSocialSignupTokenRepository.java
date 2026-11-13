package ticketing.account.account.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ticketing.account.account.domain.model.SocialSignupToken;

public interface JpaSocialSignupTokenRepository extends JpaRepository<SocialSignupToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT t
			FROM SocialSignupToken t
			WHERE t.tokenHash = :tokenHash
			"""
	)
	Optional<SocialSignupToken> findByTokenHashForUpdate(
			@Param("tokenHash") String tokenHash
	);
}
