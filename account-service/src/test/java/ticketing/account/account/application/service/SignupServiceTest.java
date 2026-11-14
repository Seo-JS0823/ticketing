package ticketing.account.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class SignupServiceTest {

	private static final String CODE = "123456";
	
	@Autowired
	private SignupService signupService;
	
	@Autowired
	private SocialSignupTokenService socialSignupTokenService;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Test
	@DisplayName("같은 이메일로 동시에 가입해도 한 번만 성공하고 나머지는 EMAIL_ALREADY_IN_USE")
	void signupService_test1() throws Exception {
		// 같은 이메일로 인증을 두 번 마친 상황 (토큰은 서로 다름)
		saveVerified("race@example.com", "race-token-1");
		saveVerified("race@example.com", "race-token-2");
		
		List<String> outcomes = runConcurrently(List.of(
				() -> signupService.signupWithEmail("abcd123!", "티켓러", "race-token-1"),
				() -> signupService.signupWithEmail("abcd123!", "티켓러", "race-token-2")
		));
		
		assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", AccountErrorCode.EMAIL_ALREADY_IN_USE.getCode());
	}
	
	@Test
	@DisplayName("같은 카카오 계정으로 동시에 가입해도 한 번만 성공하고 나머지는 SOCIAL_IDENTITY_IN_USE")
	void signupService_test2() throws Exception {
		// 같은 카카오 계정으로 로그인을 두 번 해서 signupToken을 두 개 받은 상황 (이메일은 서로 다름)
		String signupToken1 = socialSignupTokenService.issue(SocialProvider.KAKAO, "1234567890", null);
		String signupToken2 = socialSignupTokenService.issue(SocialProvider.KAKAO, "1234567890", null);
		saveVerified("kakao-race-1@example.com", "kakao-race-token-1");
		saveVerified("kakao-race-2@example.com", "kakao-race-token-2");
		
		List<String> outcomes = runConcurrently(List.of(
				() -> signupService.signupWithKakao(signupToken1, "kakao-race-token-1", "티켓러"),
				() -> signupService.signupWithKakao(signupToken2, "kakao-race-token-2", "티켓러")
		));
		
		assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", AccountErrorCode.SOCIAL_IDENTITY_IN_USE.getCode());
	}
	
	// 여러 가입 요청을 동시에 실행하고, 각 결과를 "SUCCESS" 또는 에러 코드로 돌려준다.
	private List<String> runConcurrently(List<Callable<?>> signups) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		
		List<Future<String>> results = new ArrayList<>();
		
		try (ExecutorService executor = Executors.newFixedThreadPool(signups.size())) {
			for (Callable<?> signup : signups) {
				results.add(executor.submit(() -> {
					start.await();
					
					try {
						signup.call();
						
						return "SUCCESS";
					} catch (AccountException e) {
						return e.getErrorCode().getCode();
					}
				}));
			}
			
			start.countDown();
			
			List<String> outcomes = new ArrayList<>();
			
			for (Future<String> result : results) {
				outcomes.add(result.get());
			}
			
			return outcomes;
		}
	}
	
	// 코드 검증까지 끝난 인증 요청 저장
	private EmailVerification saveVerified(String email, String token) {
		Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		
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
}