package ticketing.account.account.domain.repository;

import java.util.Optional;
import java.util.UUID;

import ticketing.account.account.domain.model.EmailVerification;

public interface EmailVerificationRepository {

	EmailVerification save(EmailVerification verification);
	
	// 행 락
	// 시도 횟수 카운트
	Optional<EmailVerification> findByIdForUpdate(UUID verificationId);
}
