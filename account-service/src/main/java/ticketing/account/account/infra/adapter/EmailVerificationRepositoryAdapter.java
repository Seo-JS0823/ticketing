package ticketing.account.account.infra.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.EmailVerificationStatus;
import ticketing.account.account.domain.repository.EmailVerificationRepository;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {
	
	private final JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Override
	public EmailVerification save(EmailVerification verification) {
		return jpaEmailVerificationRepository.save(verification);
	}

	@Override
	public Optional<EmailVerification> findByIdForUpdate(UUID verificationId) {
		return jpaEmailVerificationRepository.findByIdForUpdate(verificationId);
	}

	@Override
	public List<EmailVerification> findPendingForUpdate(Email email, EmailVerificationPurpose purpose) {
		return jpaEmailVerificationRepository.findByEmailAndPurposeAndStatusForUpdate(
				email.getEmail(),
				purpose,
				EmailVerificationStatus.PENDING
		);
	}

	@Override
	public Optional<EmailVerification> findByTokenHashForUpdate(String tokenHash) {
		return jpaEmailVerificationRepository.findByTokenHashForUpdate(tokenHash);
	}

}
