package ticketing.account.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.LoginAttemptLimiter;
import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;

@Service
@RequiredArgsConstructor
public class EmailLoginService {

	private final AccountRepository accountRepository;
	
	private final PasswordHasher passwordHasher;
	
	private final LoginAttemptLimiter loginAttemptLimiter;
	
	private final TokenService tokenService;
	
	private final Clock clock;
	
	public AuthResult login(String email, String password, String clientIp) {
		loginAttemptLimiter.checkAllowed(email, clientIp);
		
		Optional<Email> loginEmail = toEmail(email);
		
		if (loginEmail.isEmpty() || password == null || password.isBlank()) {
			throw loginFailed(email, clientIp);
		}
		
		Optional<Account> account = accountRepository.findNonWithdrawnByEmail(loginEmail.get());
		
		if (account.isPresent()) {
			if (!matchesPassword(account.get(), password)) {
				throw loginFailed(email, clientIp);
			}
			
			loginAttemptLimiter.recordSuccess(email, clientIp);
			
			return tokenService.issue(account.get());
		}
		
		if (hasRecoverableWithdrawnAccount(loginEmail.get(), password)) {
			throw new AuthException(AuthErrorCode.ACCOUNT_WITHDRAWN);
		}
		
		throw loginFailed(email, clientIp);
	}
	
	private Optional<Email> toEmail(String email) {
		try {
			return Optional.of(Email.of(email));
		} catch (AccountException e) {
			return Optional.empty();
		}
	}
	
	private boolean hasRecoverableWithdrawnAccount(Email email, String password) {
		Instant now = Instant.now(clock);
		
		return accountRepository.findWithdrawnByEmail(email).stream()
				.filter(account -> account.getRecoveryExpiresAt() != null)
				.filter(account -> account.getRecoveryExpiresAt().isAfter(now))
				.anyMatch(account -> matchesPassword(account, password));
	}
	
	private boolean matchesPassword(Account account, String password) {
		if (account.getCredential() == null) {
			return false;
		}
		
		return passwordHasher.matches(password, account.getCredential().getPasswordHash());
	}
	
	private AuthException loginFailed(String email, String clientIp) {
		loginAttemptLimiter.recordFailure(email, clientIp);
		
		return new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
	}
}
