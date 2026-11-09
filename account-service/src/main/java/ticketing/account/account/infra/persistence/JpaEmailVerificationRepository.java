package ticketing.account.account.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ticketing.account.account.domain.model.EmailVerification;

public interface JpaEmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT v
			FROM EmailVerification v
			WHERE v.id = :id
			"""
	)
	Optional<EmailVerification> findByIdForUpdate(@Param("id") UUID id);
}
