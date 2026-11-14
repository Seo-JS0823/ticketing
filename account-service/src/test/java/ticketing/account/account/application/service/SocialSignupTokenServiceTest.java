package ticketing.account.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.result.SocialIdentity;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.model.SocialSignupToken;
import ticketing.account.account.infra.persistence.JpaSocialSignupTokenRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class SocialSignupTokenServiceTest {

	private static final String KAKAO_USER_ID = "1234567890";
	
	@Autowired
	private SocialSignupTokenService socialSignupTokenService;
	
	@Autowired
	private JpaSocialSignupTokenRepository jpaSocialSignupTokenRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Autowired
	private TransactionTemplate transactionTemplate;
	
	@Test
	@DisplayName("발급하면 원문이 아닌 해시와 소셜 사용자 정보를 저장")
	void socialSignupTokenService_test1() {
		String token = socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, "kakao@example.com");
		
		SocialSignupToken saved = jpaSocialSignupTokenRepository.findAll().get(0);
		
		assertThat(saved.getTokenHash()).isEqualTo(verificationCrypto.hash(token));
		assertThat(saved.getTokenHash()).isNotEqualTo(token);
		assertThat(saved.getProviderUserId()).isEqualTo(KAKAO_USER_ID);
		assertThat(saved.getProviderEmail()).isEqualTo("kakao@example.com");
	}
	
	@Test
	@DisplayName("토큰을 사용하면 소셜 사용자 정보를 돌려주고 사용 시각 저장")
	void socialSignupTokenService_test2() {
		String token = socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, null);
		
		SocialIdentity identity = consume(token);
		
		assertThat(identity.provider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(identity.providerUserId()).isEqualTo(KAKAO_USER_ID);
		assertThat(jpaSocialSignupTokenRepository.findAll().get(0).getConsumedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("같은 토큰은 두 번 사용 불가")
	void socialSignupTokenService_test3() {
		String token = socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, null);
		consume(token);
		
		assertInvalidSignupToken(() -> consume(token));
	}
	
	@Test
	@DisplayName("없는 토큰, 빈 값, 만료된 토큰 거부")
	void socialSignupTokenService_test4() {
		saveExpired("expired-token");
		
		assertInvalidSignupToken(() -> consume("unknown-token"));
		assertInvalidSignupToken(() -> consume(""));
		assertInvalidSignupToken(() -> consume("expired-token"));
	}
	
	@Test
	@DisplayName("바깥 트랜잭션이 롤백되면 토큰을 다시 사용 가능")
	void socialSignupTokenService_test5() {
		String token = socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, null);
		
		transactionTemplate.executeWithoutResult(status -> {
			socialSignupTokenService.consume(token);
			
			status.setRollbackOnly();
		});
		
		assertThat(consume(token).providerUserId()).isEqualTo(KAKAO_USER_ID);
	}
	
	@Test
	@DisplayName("동시에 사용해도 한 번만 성공")
	void socialSignupTokenService_test6() throws Exception {
		String token = socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, null);
		
		int requests = 5;
		
		CountDownLatch start = new CountDownLatch(1);
		
		List<Future<Boolean>> results = new ArrayList<>();
		
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					
					try {
						consume(token);
						
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
	
	@Test
	@DisplayName("트랜잭션 밖에서 사용하면 예외 발생")
	void socialSignupTokenService_test7() {
		assertThatThrownBy(() -> socialSignupTokenService.consume("any"))
				.isInstanceOf(IllegalTransactionStateException.class);
	}
	
	// 가입 트랜잭션 안에서 호출하는 상황을 흉내 낸다.
	private SocialIdentity consume(String token) {
		return transactionTemplate.execute(status -> socialSignupTokenService.consume(token));
	}
	
	// 31분 전에 발급된 토큰 (이미 만료)
	private void saveExpired(String token) {
		Instant issuedAt = Instant.now().minus(Duration.ofMinutes(31));
		
		jpaSocialSignupTokenRepository.save(SocialSignupToken.issue(
				SocialProvider.KAKAO,
				KAKAO_USER_ID,
				null,
				verificationCrypto.hash(token),
				issuedAt
		));
	}
	
	private void assertInvalidSignupToken(ThrowingCallable callable) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN);
	}
}