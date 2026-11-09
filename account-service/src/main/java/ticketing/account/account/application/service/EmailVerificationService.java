package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.VerificationMailType;
import ticketing.account.account.application.dto.Delivery;
import ticketing.account.account.application.dto.VerificationMailRequested;
import ticketing.account.account.application.result.EmailVerificationResult;
import ticketing.account.account.application.result.EmailVerificationTokenResult;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.EmailVerificationErrorCode;
import ticketing.account.account.domain.exception.EmailVerificationException;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.domain.repository.EmailVerificationRepository;
import ticketing.account.global.exception.CommonErrorCode;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private final EmailVerificationRepository emailVerificationRepository;
	
	private final AccountRepository accountRepository;
	
	private final VerificationCrypto verificationCrypto;
	
	private final ApplicationEventPublisher eventPublisher;
	
	private final Clock clock;
	
	/*
	 * 계정 존재 여부 상관 X
	 * 메일 발송 여부와 관계없이 요청 행 생성
	 */
	@Transactional
	public EmailVerificationResult request(
			String rawEmail,
			EmailVerificationPurpose purpose
	) {
		Email email = Email.of(rawEmail);
		
		Delivery delivery = resolveDelivery(email, purpose);
		
		String code = verificationCrypto.generateCode();
		
		EmailVerification verification = emailVerificationRepository.save(
				EmailVerification.request(
						delivery.accountId(),
						email,
						purpose,
						verificationCrypto.hash(code),
						Instant.now(clock)
				)
		);
		
		if (delivery.mailType() != VerificationMailType.NONE) {
			eventPublisher.publishEvent(new VerificationMailRequested(
					verification.getId(),
					email.getEmail(),
					purpose,
					delivery.mailType(),
					code
			));
		}
		
		return new EmailVerificationResult(
				verification.getId(),
				verification.getExpiresAt()
		);
	}
	
	@Transactional
	public EmailVerificationResult resend(UUID verificationId) {
		EmailVerification previous = findForUpdate(verificationId);
		
		previous.cancelForResend(Instant.now(clock));
		
		return createVerification(previous.getEmail(), previous.getPurpose());
	}
	
	@Transactional(noRollbackFor = EmailVerificationException.class)
	public EmailVerificationTokenResult verify(UUID verificationId, String code) {
		EmailVerification verification = findForUpdate(verificationId);
		
		String token = verificationCrypto.generateToken();
		
		verification.verify(
				verificationCrypto.hash(code),
				verificationCrypto.hash(token),
				Instant.now(clock)
		);
		
		return new EmailVerificationTokenResult(token, verification.getTokenExpiresAt());
	}
	
	@Transactional
	public void cancel(UUID verificationId) {
		findForUpdate(verificationId).cancel();
	}
	
	private EmailVerification findForUpdate(UUID verificationId) {
		return emailVerificationRepository.findByIdForUpdate(verificationId)
				.orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_NOT_FOUND));
	}
	
	private EmailVerificationResult createVerification(Email email, EmailVerificationPurpose purpose) {
		Delivery delivery = resolveDelivery(email, purpose);
		
		String code = verificationCrypto.generateCode();
		
		EmailVerification verification = emailVerificationRepository.save(
				EmailVerification.request(
						delivery.accountId(),
						email,
						purpose,
						verificationCrypto.hash(code),
						Instant.now(clock)
				)
		);
		
		if (delivery.mailType() != VerificationMailType.NONE) {
			eventPublisher.publishEvent(new VerificationMailRequested(
					verification.getId(),
					email.getEmail(),
					purpose,
					delivery.mailType(),
					code
			));
		}
		
		return new EmailVerificationResult(
				verification.getId(),
				verification.getExpiresAt()
		);
	}
	
	private Delivery resolveDelivery(Email email, EmailVerificationPurpose purpose) {
		return switch (purpose) {
			case SIGN_UP -> accountRepository.existsNonWithdrawnByEmail(email)
					? new Delivery(null, VerificationMailType.ALREADY_REGISTERED)
					: new Delivery(null, VerificationMailType.CODE);
	
			case PASSWORD_RESET -> accountRepository.findNonWithdrawnByEmail(email)
					.map(account -> new Delivery(account.getId(), VerificationMailType.CODE))
					.orElseGet(() -> new Delivery(null, VerificationMailType.NONE));
			
			// TODO: CHANGE_EMAIL, SENSITIVE_ACTION 로그인 구현 후 추가
			
			// TODO: 복구 목적은 복구 API 구현 후 추가
			
			default -> throw new EmailVerificationException(CommonErrorCode.INVALID_REQUEST);
		};
	}
}
