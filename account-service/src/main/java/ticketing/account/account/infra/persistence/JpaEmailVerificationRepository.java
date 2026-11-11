package ticketing.account.account.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.EmailVerificationStatus;

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
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT v
			FROM EmailVerification v
			WHERE v.email.email = :email
				AND v.purpose = :purpose
				AND v.status = :status
			"""
	)
	List<EmailVerification> findByEmailAndPurposeAndStatusForUpdate(
			@Param("email") String email,
			@Param("purpose") EmailVerificationPurpose purpose,
			@Param("status") EmailVerificationStatus status
	);
}
