package ticketing.account.account.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;

public interface EmailVerificationRepository {

	EmailVerification save(EmailVerification verification);
	
	// 행 락
	// 시도 횟수 카운트
	Optional<EmailVerification> findByIdForUpdate(UUID verificationId);
	
	List<EmailVerification> findPendingForUpdate(Email email, EmailVerificationPurpose purpose);
	
	Optional<EmailVerification> findByTokenHashForUpdate(String tokenHash);
}
