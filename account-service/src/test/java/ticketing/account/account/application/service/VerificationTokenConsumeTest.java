package ticketing.account.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.VerificationMailSender;
import ticketing.account.account.application.result.VerifiedEmail;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.EmailVerificationErrorCode;
import ticketing.account.account.domain.exception.EmailVerificationException;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class VerificationTokenConsumeTest {

	private static final String CODE = "123456";
	
	@Autowired
	private EmailVerificationService emailVerificationService;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Autowired
	private TransactionTemplate transactionTemplate;
	
	@MockitoBean
	private VerificationMailSender verificationMailSender;
	
	@Test
	@DisplayName("토큰을 사용하면 이메일과 검증 시각을 돌려주고 사용 시각 저장")
	void consumeService_test1() {
		String token = "signup-token";
		EmailVerification saved = saveVerified("consume@example.com", token, Instant.now());
		
		VerifiedEmail result = consume(token, EmailVerificationPurpose.SIGN_UP);
		
		assertThat(result.verifiedAt()).isEqualTo(saved.getVerifiedAt());
		assertThat(find(saved).getConsumedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("같은 토큰은 두 번 사용 불가")
	void consumeService_test2() {
		saveVerified("twice@example.com", "twice-token", Instant.now());
		consume("twice-token", EmailVerificationPurpose.SIGN_UP);
		
		assertInvalidToken(() -> consume("twice-token", EmailVerificationPurpose.SIGN_UP));
	}
	
	@Test
	@DisplayName("없는 토큰과 만료된 토큰 거부")
	void consumeService_test3() {
		saveVerified("expire@example.com", "expired-token", Instant.now().minus(Duration.ofMinutes(11)));
		
		assertInvalidToken(() -> consume("unknown-token", EmailVerificationPurpose.SIGN_UP));
		assertInvalidToken(() -> consume("expired-token", EmailVerificationPurpose.SIGN_UP));
	}
	
	@Test
	@DisplayName("바깥 트랜잭션이 롤백되면 토큰을 다시 사용 가능")
	void consumeService_test4() {
		saveVerified("rollback@example.com", "rollback-token", Instant.now());
		
		transactionTemplate.executeWithoutResult(status -> {
			emailVerificationService.consumeToken("rollback-token", EmailVerificationPurpose.SIGN_UP);
			
			status.setRollbackOnly();
		});
		
		assertThat(consume("rollback-token", EmailVerificationPurpose.SIGN_UP).email())
		.isEqualTo(Email.of("rollback@example.com"));
	}
	
	@Test
	@DisplayName("동시에 사용해도 한 번만 성공")
	void consumeService_test5() throws Exception {
		saveVerified("race@example.com", "race-token", Instant.now());
		
		int requests = 5;
		
		CountDownLatch start = new CountDownLatch(1);
		
		List<Future<Boolean>> results = new ArrayList<>();
		
		try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				results.add(executor.submit(() -> {
					start.await();
					
					try {
						consume("race-token", EmailVerificationPurpose.SIGN_UP);
						
						return true;
					} catch (EmailVerificationException e) {
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
	@DisplayName("트랜잭션 밖에서 호출하면 예외 발생")
	void consumeService_test6() {
		assertThatThrownBy(() -> emailVerificationService.consumeToken("any", EmailVerificationPurpose.SIGN_UP))
				.isInstanceOf(IllegalTransactionStateException.class);
	}
	
	private VerifiedEmail consume(String token, EmailVerificationPurpose purpose) {
		return transactionTemplate.execute(status -> emailVerificationService.consumeToken(token, purpose));
	}
	
	private EmailVerification saveVerified(String email, String token, Instant at) {
		Instant verifiedAt = at.truncatedTo(ChronoUnit.MICROS);
		EmailVerification verification = EmailVerification.request(
				null,
				Email.of(email),
				EmailVerificationPurpose.SIGN_UP,
				verificationCrypto.hash(CODE),
				verifiedAt.minusSeconds(30)
		);
		
		verification.verify(verificationCrypto.hash(CODE), verificationCrypto.hash(token), verifiedAt);
		
		return jpaEmailVerificationRepository.save(verification);
	}
	
	private EmailVerification find(EmailVerification verification) {
		return jpaEmailVerificationRepository.findById(verification.getId()).orElseThrow();
	}
	
	private void assertInvalidToken(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOf(EmailVerificationException.class)
				.extracting(e -> ((EmailVerificationException) e).getErrorCode())
				.isEqualTo(EmailVerificationErrorCode.INVALID_VERIFICATION_TOKEN);
	}
}
