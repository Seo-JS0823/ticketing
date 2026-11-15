package ticketing.account.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.auth.application.result.RefreshTokenResult;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;
import ticketing.account.auth.domain.model.RefreshToken;
import ticketing.account.auth.infra.persistence.JpaRefreshTokenRepository;
import ticketing.account.global.crypto.TokenCrypto;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class TokenServiceTest {

	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private JpaRefreshTokenRepository jpaRefreshTokenRepository;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private TokenCrypto tokenCrypto;
	
	@Autowired
	private TransactionTemplate transactionTemplate;
	
	@Test
	@DisplayName("발급하면 원문이 아닌 해시를 저장")
	void refreshTokenService_test1() {
		UUID accountId = saveAccount("issue@example.com", "ISSUE001");
		
		RefreshTokenResult result = tokenService.issueRefreshToken(accountId);
		
		RefreshToken saved = jpaRefreshTokenRepository.findByTokenHash(tokenCrypto.hash(result.refreshToken())).orElseThrow();
		
		assertThat(result.accountId()).isEqualTo(accountId);
		assertThat(result.expiresIn()).isEqualTo(RefreshToken.TTL.toSeconds());
		assertThat(saved.getAccountId()).isEqualTo(accountId);
		assertThat(saved.getTokenHash()).isNotEqualTo(result.refreshToken());
	}
	
	@Test
	@DisplayName("재발급하면 새 토큰과 같은 계정을 돌려주고 이전 토큰은 사용 처리")
	void refreshTokenService_test2() {
		UUID accountId = saveAccount("rotate@example.com", "ROTATE01");
		String first = tokenService.issueRefreshToken(accountId).refreshToken();
		
		RefreshTokenResult rotated = tokenService.rotate(first);
		
		RefreshToken firstRow = jpaRefreshTokenRepository.findByTokenHash(tokenCrypto.hash(first)).orElseThrow();
		RefreshToken secondRow = jpaRefreshTokenRepository.findByTokenHash(tokenCrypto.hash(rotated.refreshToken())).orElseThrow();
		
		assertThat(rotated.accountId()).isEqualTo(accountId);
		assertThat(rotated.refreshToken()).isNotEqualTo(first);
		assertThat(firstRow.getUsedAt()).isNotNull();
		assertThat(secondRow.getSessionId()).isEqualTo(firstRow.getSessionId());
	}
	
	@Test
	@DisplayName("이미 사용한 토큰이 다시 들어오면 거부하고 그 세션 전체 폐기")
	void refreshTokenService_test3() {
		UUID accountId = saveAccount("reuse@example.com", "REUSE001");
		String first = tokenService.issueRefreshToken(accountId).refreshToken();
		String second = tokenService.rotate(first).refreshToken();
		
		assertInvalidRefreshToken(() -> tokenService.rotate(first));
		assertInvalidRefreshToken(() -> tokenService.rotate(second));
	}
	
	@Test
	@DisplayName("없는 토큰과 빈 값 거부")
	void refreshTokenService_test4() {
		assertInvalidRefreshToken(() -> tokenService.rotate("unknown-token"));
		assertInvalidRefreshToken(() -> tokenService.rotate(""));
		assertInvalidRefreshToken(() -> tokenService.rotate(null));
	}
	
	@Test
	@DisplayName("로그아웃하면 그 세션만 폐기하고 다른 기기 세션은 유지")
	void refreshTokenService_test5() {
		UUID accountId = saveAccount("logout@example.com", "LOGOUT01");
		String web = tokenService.issueRefreshToken(accountId).refreshToken();
		String app = tokenService.issueRefreshToken(accountId).refreshToken();
		
		tokenService.revoke(web);
		
		assertInvalidRefreshToken(() -> tokenService.rotate(web));
		assertThat(tokenService.rotate(app).accountId()).isEqualTo(accountId);
	}
	
	@Test
	@DisplayName("계정 전체 폐기는 그 계정의 세션만 폐기")
	void refreshTokenService_test6() {
		UUID accountId = saveAccount("all@example.com", "ALL00001");
		UUID otherAccountId = saveAccount("other@example.com", "OTHER001");
		String web = tokenService.issueRefreshToken(accountId).refreshToken();
		String app = tokenService.issueRefreshToken(accountId).refreshToken();
		String other = tokenService.issueRefreshToken(otherAccountId).refreshToken();
		
		tokenService.revokeAll(accountId);
		
		assertInvalidRefreshToken(() -> tokenService.rotate(web));
		assertInvalidRefreshToken(() -> tokenService.rotate(app));
		assertThat(tokenService.rotate(other).accountId()).isEqualTo(otherAccountId);
	}
	
	@Test
	@DisplayName("바깥 트랜잭션이 롤백되면 계정 전체 폐기도 롤백")
	void refreshTokenService_test7() {
		UUID accountId = saveAccount("rollback@example.com", "ROLLBACK");
		String token = tokenService.issueRefreshToken(accountId).refreshToken();
		
		transactionTemplate.executeWithoutResult(status -> {
			tokenService.revokeAll(accountId);
			
			status.setRollbackOnly();
		});
		
		assertThat(tokenService.rotate(token).accountId()).isEqualTo(accountId);
	}
	
	@Test
	@DisplayName("같은 토큰으로 동시에 재발급해도 한 번만 성공")
	void refreshTokenService_test8() throws Exception {
		UUID accountId = saveAccount("race@example.com", "RACE0001");
		String token = tokenService.issueRefreshToken(accountId).refreshToken();
		
		int requests = 5;
		
		CountDownLatch start = new CountDownLatch(1);
		
		List<Future<Boolean>> results = new ArrayList<>();
		
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					
					try {
						tokenService.rotate(token);
						
						return true;
					} catch (AuthException e) {
						return false;
					}
				}));
			}
			
			start.countDown();
			
			int successes = 0;
			
			for (Future<Boolean> result : results) {
				if (result.get()) {
					successes++;
				}
			}
			
			assertThat(successes).isEqualTo(1);
		}
	}
	
	private UUID saveAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return accountRepository.save(
				Account.register(
						PublicIdentifier.of("티켓러", tag),
						Email.of(email),
						now,
						now
				)
		).getId();
	}
	
	private void assertInvalidRefreshToken(ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
	}
}