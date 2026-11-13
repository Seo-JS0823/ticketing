package ticketing.account.account.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.EmailVerification;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.infra.persistence.JpaEmailVerificationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class SignupApiTest {

	private static final String CODE = "123456";
	
	private static final String PASSWORD = "abcd123!";
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private JpaEmailVerificationRepository jpaEmailVerificationRepository;
	
	@Autowired
	private VerificationCrypto verificationCrypto;
	
	@Autowired
	private PasswordHasher passwordHasher;
	
	@Autowired
	private TransactionTemplate transactionTemplate;
	
	@Test
	@DisplayName("인증 완료 토큰으로 가입하면 201과 계정 정보를 주고 인증 완료 토큰 사용 처리")
	void signupAPI_test1() throws Exception {
		EmailVerification verification = saveVerified("New@Example.com", "signup-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "signup-token")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.expiresIn").value(900))
				.andExpect(jsonPath("$.data.account.email").value("new@example.com"))
				.andExpect(jsonPath("$.data.account.nickname").value("티켓러"))
				.andExpect(jsonPath("$.data.account.status").value("ACTIVE"));
		
		Account account = accountRepository.findNonWithdrawnByEmail(Email.of("new@example.com")).orElseThrow();
		
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getEmailVerifiedAt()).isNotNull();
		assertThat(account.getPublicIdentifier().getTag()).matches("^[A-Z0-9]{8}$");
		assertThat(find(verification).getConsumedAt()).isNotNull();
	}
	
	@Test
	@DisplayName("비밀번호는 원문이 아닌 해시로 저장")
	void signupAPI_test2() throws Exception {
		saveVerified("hash@example.com", "hash-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "hash-token")
				.andExpect(status().isCreated());
		
		// credential은 LAZY라 트랜잭션 안에서 읽는다.
		String passwordHash = transactionTemplate.execute(status -> accountRepository
				.findNonWithdrawnByEmail(Email.of("hash@example.com"))
				.orElseThrow()
				.getCredential()
				.getPasswordHash());
		
		assertThat(passwordHash).isNotEqualTo(PASSWORD);
		assertThat(passwordHasher.matches(PASSWORD, passwordHash)).isTrue();
	}
	
	@Test
	@DisplayName("웹은 Refresh Token을 쿠키로 전달하고 바디에는 넣지 않음")
	void signupAPI_test3() throws Exception {
		saveVerified("web@example.com", "web-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "web-token")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.refreshToken").doesNotExist())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
						containsString("refresh_token="),
						containsString("Path=/api/v1/auth"),
						containsString("Max-Age=1209600"),
						containsString("Secure"),
						containsString("HttpOnly"),
						containsString("SameSite=Lax")
				)));
	}
	
	@Test
	@DisplayName("앱은 Refresh Token을 바디로 전달하고 쿠키는 보내지 않음")
	void signupAPI_test4() throws Exception {
		saveVerified("app@example.com", "app-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "앱유저", "app-token", "APP")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
	
	@Test
	@DisplayName("같은 인증 완료 토큰으로 두 번 가입 불가")
	void signupAPI_test5() throws Exception {
		saveVerified("twice@example.com", "twice-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "twice-token")
				.andExpect(status().isCreated());
		
		signup(PASSWORD, "티켓러", "twice-token")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("이미 가입된 이메일이면 EMAIL_ALREADY_IN_USE, 인증 완료 토큰은 소모되지 않음")
	void signupAPI_test6() throws Exception {
		saveAccount("dup@example.com", "DUP00001");
		EmailVerification verification = saveVerified("dup@example.com", "dup-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "dup-token")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
		
		assertThat(find(verification).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("비밀번호 규칙 위반이면 INVALID_PASSWORD_PATTERN, 인증 완료 토큰은 소모되지 않음")
	void signupAPI_test7() throws Exception {
		EmailVerification verification = saveVerified("weak@example.com", "weak-token", EmailVerificationPurpose.SIGN_UP);
		
		signup("abcd1234", "티켓러", "weak-token")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD_PATTERN"));
		
		assertThat(find(verification).getConsumedAt()).isNull();
	}
	
	@Test
	@DisplayName("닉네임 형식이 틀리면 INVALID_NICKNAME_PATTERN")
	void signupAPI_test8() throws Exception {
		saveVerified("nick@example.com", "nick-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티", "nick-token")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_NICKNAME_PATTERN"));
	}
	
	@Test
	@DisplayName("가입 목적이 아닌 토큰과 없는 토큰은 INVALID_VERIFICATION_TOKEN")
	void signupAPI_test9() throws Exception {
		saveVerified("reset@example.com", "reset-token", EmailVerificationPurpose.PASSWORD_RESET);
		
		signup(PASSWORD, "티켓러", "reset-token")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
		
		signup(PASSWORD, "티켓러", "unknown-token")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
	}
	
	@Test
	@DisplayName("알 수 없는 X-Client-Type이면 INVALID_REQUEST")
	void signupAPI_test10() throws Exception {
		saveVerified("client@example.com", "client-token", EmailVerificationPurpose.SIGN_UP);
		
		signup(PASSWORD, "티켓러", "client-token", "DESKTOP")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
	
	// X-Client-Type 없이 요청 (WEB)
	private ResultActions signup(String password, String nickname, String token) throws Exception {
		return mockMvc.perform(signupRequest(password, nickname, token));
	}
	
	private ResultActions signup(String password, String nickname, String token, String clientType) throws Exception {
		return mockMvc.perform(signupRequest(password, nickname, token)
				.header("X-Client-Type", clientType));
	}
	
	private MockHttpServletRequestBuilder signupRequest(String password, String nickname, String token) {
		return post("/api/v1/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
						{"password": "%s", "nickname": "%s", "verificationToken": "%s"}
						""".formatted(password, nickname, token)
				);
	}
	
	// 코드 검증까지 끝난 인증 요청 저장
	private EmailVerification saveVerified(String email, String token, EmailVerificationPurpose purpose) {
		Instant verifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		
		EmailVerification verification = EmailVerification.request(
				null,
				Email.of(email),
				purpose,
				verificationCrypto.hash(CODE),
				verifiedAt.minusSeconds(30)
		);
		
		verification.verify(verificationCrypto.hash(CODE), verificationCrypto.hash(token), verifiedAt);
		
		return jpaEmailVerificationRepository.save(verification);
	}
	
	private EmailVerification find(EmailVerification verification) {
		return jpaEmailVerificationRepository.findById(verification.getId()).orElseThrow();
	}
	
	private Account saveAccount(String email, String tag) {
		Instant now = Instant.now();
		
		return accountRepository.save(
				Account.register(
						PublicIdentifier.of("기존회원", tag),
						Email.of(email),
						now,
						now
				)
		);
	}
}