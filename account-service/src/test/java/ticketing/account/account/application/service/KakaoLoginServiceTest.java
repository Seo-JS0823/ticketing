package ticketing.account.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ticketing.account.account.application.port.KakaoOAuthClient;
import ticketing.account.account.application.properties.KakaoProperties;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.KakaoLoginResult;
import ticketing.account.account.application.result.KakaoUser;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;

/*
 * 스프링 없이 Mockito로 협력 객체를 가짜로 바꾼 단위 테스트
 * 탈퇴·정지 계정을 만드는 도메인 행위가 아직 없어서, 계정도 가짜(mock)로 만들어 상태를 정한다.
 */
public class KakaoLoginServiceTest {

	private static final String KAKAO_USER_ID = "1234567890";
	
	private static final String REDIRECT_URI = "http://localhost:3000/oauth/kakao";
	
	private KakaoOAuthClient kakaoOAuthClient;
	
	private AccountRepository accountRepository;
	
	private SocialSignupTokenService socialSignupTokenService;
	
	private AuthTokenService authTokenService;
	
	private KakaoLoginService kakaoLoginService;
	
	@BeforeEach
	void setUp() {
		kakaoOAuthClient = mock(KakaoOAuthClient.class);
		accountRepository = mock(AccountRepository.class);
		socialSignupTokenService = mock(SocialSignupTokenService.class);
		authTokenService = mock(AuthTokenService.class);
		
		KakaoProperties kakaoProperties = new KakaoProperties(
				"test-client-id",
				"test-client-secret",
				123456L,
				List.of(REDIRECT_URI),
				"https://kauth.kakao.com",
				"https://kapi.kakao.com",
				Duration.ofSeconds(3),
				Duration.ofSeconds(5)
		);
		
		kakaoLoginService = new KakaoLoginService(
				kakaoOAuthClient,
				kakaoProperties,
				accountRepository,
				socialSignupTokenService,
				authTokenService
		);
	}
	
	@Test
	@DisplayName("연결된 활성 계정이면 LOGIN과 토큰 발급, signupToken은 발급하지 않음")
	void kakaoLoginService_test1() {
		Account account = accountWithStatus(AccountStatus.ACTIVE);
		AuthResult auth = new AuthResult(null, null, null);
		givenKakaoUser("kakao@example.com");
		when(accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID)).thenReturn(Optional.of(account));
		when(authTokenService.issue(account)).thenReturn(auth);
		
		KakaoLoginResult result = kakaoLoginService.loginWithAccessToken("app-access-token");
		
		assertThat(result.status()).isEqualTo(KakaoLoginResult.Status.LOGIN);
		assertThat(result.auth()).isSameAs(auth);
		assertThat(result.signupToken()).isNull();
		verify(socialSignupTokenService, never()).issue(any(), anyString(), any());
	}
	
	@Test
	@DisplayName("정지(SUSPENDED) 계정도 로그인 허용")
	void kakaoLoginService_test2() {
		Account account = accountWithStatus(AccountStatus.SUSPENDED);
		givenKakaoUser(null);
		when(accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID)).thenReturn(Optional.of(account));
		when(authTokenService.issue(account)).thenReturn(new AuthResult(null, null, null));
		
		KakaoLoginResult result = kakaoLoginService.loginWithAccessToken("app-access-token");
		
		assertThat(result.status()).isEqualTo(KakaoLoginResult.Status.LOGIN);
	}
	
	@Test
	@DisplayName("탈퇴 계정이면 ACCOUNT_WITHDRAWN, 토큰은 발급하지 않음")
	void kakaoLoginService_test3() {
		Account account = accountWithStatus(AccountStatus.WITHDRAWN);
		givenKakaoUser(null);
		when(accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID)).thenReturn(Optional.of(account));
		
		assertAuthError(
				() -> kakaoLoginService.loginWithAccessToken("app-access-token"),
				AuthErrorCode.ACCOUNT_WITHDRAWN
		);
		verify(authTokenService, never()).issue(any());
	}
	
	@Test
	@DisplayName("연결된 계정이 없으면 SIGNUP_REQUIRED와 signupToken, 카카오 이메일 전달")
	void kakaoLoginService_test4() {
		givenKakaoUser("kakao@example.com");
		when(accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID)).thenReturn(Optional.empty());
		when(socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, "kakao@example.com")).thenReturn("signup-token");
		
		KakaoLoginResult result = kakaoLoginService.loginWithAccessToken("app-access-token");
		
		assertThat(result.status()).isEqualTo(KakaoLoginResult.Status.SIGNUP_REQUIRED);
		assertThat(result.signupToken()).isEqualTo("signup-token");
		assertThat(result.kakaoEmail()).isEqualTo("kakao@example.com");
		assertThat(result.auth()).isNull();
		verify(authTokenService, never()).issue(any());
	}
	
	@Test
	@DisplayName("웹: 허용된 redirectUri면 인가 코드로 카카오 사용자 조회")
	void kakaoLoginService_test5() {
		when(kakaoOAuthClient.fetchUserByAuthorizationCode("auth-code", REDIRECT_URI))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, null));
		when(accountRepository.findBySocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID)).thenReturn(Optional.empty());
		when(socialSignupTokenService.issue(SocialProvider.KAKAO, KAKAO_USER_ID, null)).thenReturn("signup-token");
		
		KakaoLoginResult result = kakaoLoginService.loginWithAuthorizationCode("auth-code", REDIRECT_URI);
		
		assertThat(result.status()).isEqualTo(KakaoLoginResult.Status.SIGNUP_REQUIRED);
		assertThat(result.kakaoEmail()).isNull();
	}
	
	@Test
	@DisplayName("웹: 허용되지 않은 redirectUri면 카카오를 호출하지 않고 INVALID_SOCIAL_CREDENTIAL")
	void kakaoLoginService_test6() {
		assertAuthError(
				() -> kakaoLoginService.loginWithAuthorizationCode("auth-code", "https://evil.example.com/oauth/kakao"),
				AuthErrorCode.INVALID_SOCIAL_CREDENTIAL
		);
		verify(kakaoOAuthClient, never()).fetchUserByAuthorizationCode(anyString(), anyString());
	}
	
	private void givenKakaoUser(String email) {
		when(kakaoOAuthClient.fetchUserByAccessToken("app-access-token"))
				.thenReturn(new KakaoUser(KAKAO_USER_ID, email));
	}
	
	private Account accountWithStatus(AccountStatus status) {
		Account account = mock(Account.class);
		when(account.getStatus()).thenReturn(status);
		return account;
	}
	
	private void assertAuthError(ThrowingCallable callable, AuthErrorCode errorCode) {
		assertThatThrownBy(callable)
				.isInstanceOf(AuthException.class)
				.extracting(e -> ((AuthException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}