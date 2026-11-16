package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.PasswordPolicy;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.LoginAttemptLimiter;
import ticketing.account.auth.application.service.TokenService;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;
import ticketing.account.verification.application.result.VerifiedEmail;
import ticketing.account.verification.application.service.EmailVerificationService;
import ticketing.account.verification.domain.exception.EmailVerificationErrorCode;
import ticketing.account.verification.domain.exception.EmailVerificationException;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;

@Service
@RequiredArgsConstructor
public class PasswordService {

	private final AccountRepository accountRepository;
	
	private final EmailVerificationService emailVerificationService;
	
	private final PasswordHasher passwordHasher;
	
	private final TokenService tokenService;
	
	private final LoginAttemptLimiter loginAttemptLimiter;
	
	private final Clock clock;
	
	@Transactional
	public void resetPassword(String verificationToken, String newPassword, String clientIp) {
		PasswordPolicy.validate(newPassword);
		
		String passwordHash = passwordHasher.hash(newPassword);
		
		VerifiedEmail verified = emailVerificationService.consumeToken(
				verificationToken,
				EmailVerificationPurpose.PASSWORD_RESET
		);
		
		Account account = findTarget(verified);
		
		validateNotSameAsCurrent(account, newPassword);
		
		account.resetPassword(passwordHash, Instant.now(clock));
		
		tokenService.revokeAll(account.getId());
		
		loginAttemptLimiter.recordSuccess(verified.email().getEmail(), clientIp);
	}
	
	private Account findTarget(VerifiedEmail verified) {
		if (verified.accountId() == null) {
			throw invalidToken();
		}
		
		Account account = accountRepository.findById(verified.accountId())
				.orElseThrow(this::invalidToken);
		
		if (!account.getEmail().equals(verified.email())) {
			throw invalidToken();
		}
		
		if (account.getStatus() == AccountStatus.WITHDRAWN) {
			throw new AuthException(AuthErrorCode.ACCOUNT_WITHDRAWN);
		}
		
		return account;
	}
	
	private void validateNotSameAsCurrent(Account account, String newPassword) {
		if (account.getCredential() == null) {
			return;
		}
		
		if (passwordHasher.matches(newPassword, account.getCredential().getPasswordHash())) {
			throw new AuthException(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
		}
	}
	
	private EmailVerificationException invalidToken() {
		return new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
	}
}
