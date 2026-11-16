package ticketing.account.verification.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.service.AccountService;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.global.crypto.TokenCrypto;
import ticketing.account.global.exception.CommonErrorCode;
import ticketing.account.verification.application.dto.Delivery;
import ticketing.account.verification.application.dto.VerificationMailType;
import ticketing.account.verification.application.event.VerificationMailRequested;
import ticketing.account.verification.application.port.VerificationSendLimiter;
import ticketing.account.verification.application.result.EmailVerificationResult;
import ticketing.account.verification.application.result.EmailVerificationTokenResult;
import ticketing.account.verification.application.result.VerifiedEmail;
import ticketing.account.verification.domain.exception.EmailVerificationErrorCode;
import ticketing.account.verification.domain.exception.EmailVerificationException;
import ticketing.account.verification.domain.model.EmailVerification;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;
import ticketing.account.verification.domain.repository.EmailVerificationRepository;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private final EmailVerificationRepository emailVerificationRepository;
	
	private final AccountRepository accountRepository;
	
	private final AccountService accountService;
	
	private final TokenCrypto tokenCrypto;
	
	private final VerificationSendLimiter verificationSendLimiter;
	
	private final ApplicationEventPublisher eventPublisher;
	
	private final Clock clock;
	
	/*
	 * 계정 존재 여부 상관 X
	 * 메일 발송 여부와 관계없이 요청 행 생성
	 */
	@Transactional
	public EmailVerificationResult request(
			String rawEmail,
			EmailVerificationPurpose purpose,
			UUID accountId,
			String clientIp
	) {
		return createVerification(
				resolveDelivery(rawEmail, accountId, purpose),
				purpose,
				clientIp
		);
	}
	
	@Transactional
	public EmailVerificationResult resend(UUID verificationId, String clientIp) {
		EmailVerification previous = findForUpdate(verificationId);
		
		previous.cancelForResend(Instant.now(clock));
		
		Delivery delivery = resolveDelivery(
				previous.getEmail().getEmail(),
				previous.getAccountId(),
				previous.getPurpose()
		);
		
		return createVerification(delivery, previous.getPurpose(), clientIp);
	}
	
	@Transactional(noRollbackFor = EmailVerificationException.class)
	public EmailVerificationTokenResult verify(UUID verificationId, String code) {
		EmailVerification verification = findForUpdate(verificationId);
		
		String token = tokenCrypto.generateToken();
		
		verification.verify(
				tokenCrypto.hash(code),
				tokenCrypto.hash(token),
				Instant.now(clock)
		);
		
		return new EmailVerificationTokenResult(token, verification.getTokenExpiresAt());
	}
	
	@Transactional
	public void cancel(UUID verificationId) {
		findForUpdate(verificationId).cancel();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public VerifiedEmail consumeToken(String token, EmailVerificationPurpose purpose) {
		if (token == null || token.isBlank()) {
			throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
		}
		
		EmailVerification verification = emailVerificationRepository
				.findByTokenHashForUpdate(tokenCrypto.hash(token))
				.orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN));
		
		verification.consume(purpose, Instant.now(clock));
		
		return new VerifiedEmail(
				verification.getEmail(),
				verification.getAccountId(),
				verification.getVerifiedAt()
		);
	}
	
	private EmailVerificationResult createVerification(
			Delivery delivery,
			EmailVerificationPurpose purpose,
			String clientIp
	) {
		Email email = delivery.email();
		
		verificationSendLimiter.acquire(email, clientIp);
		
		emailVerificationRepository.findPendingForUpdate(email, purpose)
				.forEach(EmailVerification::cancel);
		
		String code = tokenCrypto.generateCode();
		
		EmailVerification verification = emailVerificationRepository.save(
				EmailVerification.request(
						delivery.accountId(),
						email,
						purpose,
						tokenCrypto.hash(code),
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
	
	private EmailVerification findForUpdate(UUID verificationId) {
		return emailVerificationRepository.findByIdForUpdate(verificationId)
				.orElseThrow(() -> new EmailVerificationException(EmailVerificationErrorCode.VERIFICATION_NOT_FOUND));
	}
	
	private Delivery resolveDelivery(String rawEmail, UUID accountId, EmailVerificationPurpose purpose) {
		if (purpose == EmailVerificationPurpose.SENSITIVE_ACTION) {
			Account account = accountService.getLoginAccount(accountId);
			
			return new Delivery(account.getEmail(), account.getId(), VerificationMailType.CODE);
		}
		
		Email email = Email.of(rawEmail);
		
		return switch (purpose) {
			case SIGN_UP -> accountRepository.existsNonWithdrawnByEmail(email)
					? new Delivery(email, null, VerificationMailType.ALREADY_REGISTERED)
					: new Delivery(email, null, VerificationMailType.CODE);
	
			case PASSWORD_RESET -> accountRepository.findNonWithdrawnByEmail(email)
					.map(account -> new Delivery(email, account.getId(), VerificationMailType.CODE))
					.orElseGet(() -> new Delivery(email, null, VerificationMailType.NONE));
			
			// TODO: CHANGE_EMAIL 로그인 구현 후 추가
			
			// TODO: 복구 목적은 복구 API 구현 후 추가
			
			default -> throw new EmailVerificationException(CommonErrorCode.INVALID_REQUEST);
		};
	}
}
