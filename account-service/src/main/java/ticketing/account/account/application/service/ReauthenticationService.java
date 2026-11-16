package ticketing.account.account.application.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.auth.application.port.LoginAttemptLimiter;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;
import ticketing.account.verification.application.result.VerifiedEmail;
import ticketing.account.verification.application.service.EmailVerificationService;
import ticketing.account.verification.domain.exception.EmailVerificationErrorCode;
import ticketing.account.verification.domain.exception.EmailVerificationException;
import ticketing.account.verification.domain.model.EmailVerificationPurpose;

@Service
@RequiredArgsConstructor
public class ReauthenticationService {

	private final PasswordHasher passwordHasher;
	
	private final LoginAttemptLimiter loginAttemptLimiter;
	
	private final EmailVerificationService emailVerificationService;
	
	public void verifyPassword(Account account, String currentPassword, String clientIp) {
		String email = account.getEmail().getEmail();
		
		loginAttemptLimiter.checkAllowed(email, clientIp);
		
		if (!account.hasPassword()) {
			throw new AccountException(AccountErrorCode.CREDENTIAL_NOT_REGISTERED);
		}
		
		if (!matchesCurrentPassword(account, currentPassword)) {
			loginAttemptLimiter.recordFailure(email, clientIp);
			
			throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
		}
	}
	
	public void recordPasswordSuccess(Account account, String clientIp) {
		loginAttemptLimiter.recordSuccess(account.getEmail().getEmail(), clientIp);
	}
	
	public VerifiedEmail consumeSensitiveActionToken(String verificationToken) {
		return emailVerificationService.consumeToken(
				verificationToken,
				EmailVerificationPurpose.SENSITIVE_ACTION
		);
	}
	
	public void validateTokenOwner(Account account, VerifiedEmail verified) {
		boolean sameAccount = account.getId().equals(verified.accountId());
		
		boolean sameEmail = account.getEmail().equals(verified.email());
		
		if (!sameAccount || !sameEmail) {
			throw new EmailVerificationException(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
		}
	}
	
	private boolean matchesCurrentPassword(Account account, String currentPassword) {
		if (currentPassword == null || currentPassword.isBlank()) {
			return false;
		}
		
		return passwordHasher.matches(currentPassword, account.getCredential().getPasswordHash());
	}
}
