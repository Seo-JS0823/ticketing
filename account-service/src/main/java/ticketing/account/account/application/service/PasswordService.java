package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

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
	
	private final AccountService accountService;
	
	private final ReauthenticationService reauthenticationService;
	
	private final TokenService tokenService;
	
	private final LoginAttemptLimiter loginAttemptLimiter;
	
	private final Clock clock;

	@Transactional
	public void changePassword(UUID accountId, String currentPassword, String newPassword, String clientIp) {
		Account account = accountService.getLoginAccount(accountId);
		
		reauthenticationService.verifyPassword(account, currentPassword, clientIp);
		
		PasswordPolicy.validate(newPassword);
		
		if (newPassword.equals(currentPassword)) {
			throw new AuthException(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
		}
		
		account.changePassword(passwordHasher.hash(newPassword), Instant.now(clock));
		
		tokenService.revokeAll(account.getId());
		
		reauthenticationService.recordPasswordSuccess(account, clientIp);
	}
	
//	@Transactional
//	public void changePassword(UUID accountId, String currentPassword, String newPassword, String clientIp) {
//		Account account = accountService.getLoginAccount(accountId);
//		
//		String email = account.getEmail().getEmail();
//		
//		loginAttemptLimiter.checkAllowed(email, clientIp);
//		
//		if (!account.hasPassword()) {
//			throw new AccountException(AccountErrorCode.CREDENTIAL_NOT_REGISTERED);
//		}
//		
//		if (!matchesCurrentPassword(account, currentPassword)) {
//			loginAttemptLimiter.recordFailure(email, clientIp);
//			
//			throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
//		}
//		
//		PasswordPolicy.validate(newPassword);
//		
//		if (newPassword.equals(currentPassword)) {
//			throw new AuthException(AuthErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
//		}
//		
//		account.changePassword(passwordHasher.hash(newPassword), Instant.now(clock));
//		
//		tokenService.revokeAll(account.getId());
//		
//		loginAttemptLimiter.recordSuccess(email, clientIp);
//	}
	
	@Transactional
	public void resetPassword(String verificationToken, String newPassword, String clientIp) {
		// 1. 규칙 검증과 해시: 행 락 이전
		PasswordPolicy.validate(newPassword);
		
		String passwordHash = passwordHasher.hash(newPassword);
		
		// 2. 인증 완료 토큰 사용: 행 락 사용
		VerifiedEmail verified = emailVerificationService.consumeToken(
				verificationToken,
				EmailVerificationPurpose.PASSWORD_RESET
		);
				
		// 3. 대상 계정 확인
		Account account = findTarget(verified);
		
		// 4. 현재 비밀번호와 같으면 거부
		validateNotSameAsCurrent(account, newPassword);
		
		// 5. 재설정
		account.resetPassword(passwordHash, Instant.now(clock));
		
		// 6. 계정의 모든 세션 폐기
		tokenService.revokeAll(account.getId());
		
		// 이 IP의 로그인 실패 횟수 초기화
		loginAttemptLimiter.recordSuccess(verified.email().getEmail(), clientIp);
	}
	
	@Transactional
	public void addPassword(UUID accountId, String password, String verificationToken) {
		PasswordPolicy.validate(password);
		
		String passwordHash = passwordHasher.hash(password);
		
		VerifiedEmail verified = reauthenticationService.consumeSensitiveActionToken(verificationToken);
		
		Account account = accountService.getLoginAccount(accountId);
		
		reauthenticationService.validateTokenOwner(account, verified);
		
		account.registerPassword(passwordHash, Instant.now(clock));
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
