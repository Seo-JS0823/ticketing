package ticketing.account.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountCredential;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.port.LoginAttemptLimiter;
import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;
import ticketing.account.global.exception.RateLimitExceededException;

public class EmailLoginServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");
	
	private static final String EMAIL = "user@example.com";
	
	private static final String PASSWORD = "abcd123!";
	
	private static final String PASSWORD_HASH = "password-hash";
	
	private static final String IP = "10.0.0.1";
	
	private AccountRepository accountRepository;
	
	private PasswordHasher passwordHasher;
	
	private LoginAttemptLimiter loginAttemptLimiter;
	
	private TokenService tokenService;
	
	private EmailLoginService emailLoginService;
	
	@BeforeEach
	void setUp() {
		accountRepository = mock(AccountRepository.class);
		passwordHasher = mock(PasswordHasher.class);
		loginAttemptLimiter = mock(LoginAttemptLimiter.class);
		tokenService = mock(TokenService.class);
		
		emailLoginService = new EmailLoginService(
				accountRepository,
				passwordHasher,
				loginAttemptLimiter,
				tokenService,
				Clock.fixed(NOW, ZoneOffset.UTC)
		);
		
		// 기본: 해당 이메일의 계정 없음, PASSWORD만 PASSWORD_HASH와 일치
		when(accountRepository.findNonWithdrawnByEmail(any())).thenReturn(Optional.empty());
		when(accountRepository.findWithdrawnByEmail(any())).thenReturn(List.of());
		when(passwordHasher.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
	}
	
	@Test
	@DisplayName("비밀번호가 맞으면 토큰 발급, 이메일+IP 카운터 초기화, 실패로 세지 않음")
	void emailLoginService_test1() {
		Account account = accountWith(AccountStatus.ACTIVE, PASSWORD_HASH, null);
		AuthResult auth = new AuthResult(null, null, null);
		givenNonWithdrawn(account);
		when(tokenService.issue(account)).thenReturn(auth);
		
		AuthResult result = emailLoginService.login(EMAIL, PASSWORD, IP);
		
		assertThat(result).isSameAs(auth);
		verify(loginAttemptLimiter).recordSuccess(EMAIL, IP);
		verify(loginAttemptLimiter, never()).recordFailure(any(), any());
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 로그인 허용")
	void emailLoginService_test2() {
		Account account = accountWith(AccountStatus.SUSPENDED, PASSWORD_HASH, null);
		givenNonWithdrawn(account);
		when(tokenService.issue(account)).thenReturn(new AuthResult(null, null, null));
		
		emailLoginService.login(EMAIL, PASSWORD, IP);
		
		verify(tokenService).issue(account);
	}
	
	@Test
	@DisplayName("비밀번호가 틀리면 INVALID_CREDENTIALS, 실패 기록, 토큰 발급 안 함")
	void emailLoginService_test3() {
		givenNonWithdrawn(accountWith(AccountStatus.ACTIVE, PASSWORD_HASH, null));
		
		assertLoginFailed(() -> emailLoginService.login(EMAIL, "wrong123!", IP));
		verify(tokenService, never()).issue(any());
	}
	
	@Test
	@DisplayName("없는 이메일이면 INVALID_CREDENTIALS, 실패 기록")
	void emailLoginService_test4() {
		assertLoginFailed(() -> emailLoginService.login("nobody@example.com", PASSWORD, IP));
		verify(loginAttemptLimiter).recordFailure("nobody@example.com", IP);
	}
	
	@Test
	@DisplayName("소셜 전용 계정(비밀번호 없음)이면 INVALID_CREDENTIALS, 실패 기록")
	void emailLoginService_test5() {
		givenNonWithdrawn(accountWith(AccountStatus.ACTIVE, null, null));
		
		assertLoginFailed(() -> emailLoginService.login(EMAIL, PASSWORD, IP));
		verify(passwordHasher, never()).matches(anyString(), anyString());
	}
	
	@Test
	@DisplayName("형식이 잘못되었거나 빈 이메일이면 계정을 조회하지 않고 INVALID_CREDENTIALS, 실패 기록")
	void emailLoginService_test6() {
		assertLoginFailed(() -> emailLoginService.login("not-an-email", PASSWORD, IP));
		assertLoginFailed(() -> emailLoginService.login("", PASSWORD, IP));
		assertLoginFailed(() -> emailLoginService.login(null, PASSWORD, IP));
		
		verify(loginAttemptLimiter).recordFailure("not-an-email", IP);
		verify(loginAttemptLimiter).recordFailure("", IP);
		verify(loginAttemptLimiter).recordFailure(null, IP);
		verify(accountRepository, never()).findNonWithdrawnByEmail(any());
	}
	
	@Test
	@DisplayName("빈 비밀번호면 계정을 조회하지 않고 INVALID_CREDENTIALS, 실패 기록")
	void emailLoginService_test7() {
		assertLoginFailed(() -> emailLoginService.login(EMAIL, "", IP));
		assertLoginFailed(() -> emailLoginService.login(EMAIL, null, IP));
		
		verify(accountRepository, never()).findNonWithdrawnByEmail(any());
	}
	
	@Test
	@DisplayName("실패 한도를 넘었으면 비밀번호를 확인하지 않고 429")
	void emailLoginService_test8() {
		doThrow(new RateLimitExceededException(Duration.ofMinutes(10)))
				.when(loginAttemptLimiter).checkAllowed(EMAIL, IP);
		
		assertThatThrownBy(() -> emailLoginService.login(EMAIL, PASSWORD, IP))
				.isInstanceOf(RateLimitExceededException.class);
		
		verify(accountRepository, never()).findNonWithdrawnByEmail(any());
		verify(tokenService, never()).issue(any());
	}
	
	@Test
	@DisplayName("복구 기간 안의 탈퇴 계정 + 맞는 비밀번호면 ACCOUNT_WITHDRAWN, 실패로 세지 않음")
	void emailLoginService_test9() {
		givenWithdrawn(accountWith(AccountStatus.WITHDRAWN, PASSWORD_HASH, NOW.plusSeconds(60)));
		
		assertAuthError(() -> emailLoginService.login(EMAIL, PASSWORD, IP), AuthErrorCode.ACCOUNT_WITHDRAWN);
		verify(loginAttemptLimiter, never()).recordFailure(any(), any());
		verify(tokenService, never()).issue(any());
	}
	
	@Test
	@DisplayName("복구 기간 안의 탈퇴 계정이어도 비밀번호가 틀리면 INVALID_CREDENTIALS")
	void emailLoginService_test10() {
		givenWithdrawn(accountWith(AccountStatus.WITHDRAWN, PASSWORD_HASH, NOW.plusSeconds(60)));
		
		assertLoginFailed(() -> emailLoginService.login(EMAIL, "wrong123!", IP));
	}
	
	@Test
	@DisplayName("복구 기간이 지난 탈퇴 계정은 비밀번호가 맞아도 INVALID_CREDENTIALS")
	void emailLoginService_test11() {
		givenWithdrawn(accountWith(AccountStatus.WITHDRAWN, PASSWORD_HASH, NOW));
		
		assertLoginFailed(() -> emailLoginService.login(EMAIL, PASSWORD, IP));
		verify(passwordHasher, never()).matches(anyString(), anyString());
	}
	
	@Test
	@DisplayName("같은 이메일로 다시 가입한 계정이 있으면 탈퇴 계정은 확인하지 않음")
	void emailLoginService_test12() {
		givenNonWithdrawn(accountWith(AccountStatus.ACTIVE, "new-password-hash", null));
		givenWithdrawn(accountWith(AccountStatus.WITHDRAWN, PASSWORD_HASH, NOW.plusSeconds(60)));
		
		// 탈퇴 계정의 비밀번호로 로그인해도 ACCOUNT_WITHDRAWN이 아니라 INVALID_CREDENTIALS
		assertLoginFailed(() -> emailLoginService.login(EMAIL, PASSWORD, IP));
		verify(accountRepository, never()).findWithdrawnByEmail(any());
	}
	
	@Test
	@DisplayName("대소문자와 앞뒤 공백이 달라도 같은 이메일로 로그인")
	void emailLoginService_test13() {
		Account account = accountWith(AccountStatus.ACTIVE, PASSWORD_HASH, null);
		givenNonWithdrawn(account);
		when(tokenService.issue(account)).thenReturn(new AuthResult(null, null, null));
		
		emailLoginService.login("  User@Example.com  ", PASSWORD, IP);
		
		verify(tokenService).issue(account);
	}
	
	private void givenNonWithdrawn(Account account) {
		when(accountRepository.findNonWithdrawnByEmail(Email.of(EMAIL))).thenReturn(Optional.of(account));
	}
	
	private void givenWithdrawn(Account account) {
		when(accountRepository.findWithdrawnByEmail(Email.of(EMAIL))).thenReturn(List.of(account));
	}
	
	// passwordHash가 null이면 소셜 전용 계정
	private Account accountWith(AccountStatus status, String passwordHash, Instant recoveryExpiresAt) {
		Account account = mock(Account.class);
		when(account.getStatus()).thenReturn(status);
		when(account.getRecoveryExpiresAt()).thenReturn(recoveryExpiresAt);
		
		if (passwordHash != null) {
			AccountCredential credential = mock(AccountCredential.class);
			when(credential.getPasswordHash()).thenReturn(passwordHash);
			when(account.getCredential()).thenReturn(credential);
		}
		
		return account;
	}
	
	// INVALID_CREDENTIALS이고 실패가 기록되었는지
	private void assertLoginFailed(ThrowingCallable callable) {
		assertAuthError(callable, AuthErrorCode.INVALID_CREDENTIALS);
		verify(loginAttemptLimiter, atLeastOnce()).recordFailure(any(), any());
		verify(loginAttemptLimiter, never()).recordSuccess(any(), any());
	}
	
	private void assertAuthError(ThrowingCallable callable, AuthErrorCode errorCode) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}