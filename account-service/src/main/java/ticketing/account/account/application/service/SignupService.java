package ticketing.account.account.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.SocialIdentity;
import ticketing.account.account.application.result.VerifiedEmail;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.EmailVerificationPurpose;
import ticketing.account.account.domain.model.PasswordPolicy;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.repository.AccountRepository;

@Service
@RequiredArgsConstructor
public class SignupService {

	private static final int MAX_TAG_ATTEMPTS = 5;
	
	private final RandomGenerator random = new SecureRandom();
	
	private final AccountRepository accountRepository;
	
	private final EmailVerificationService emailVerificationService;
	
	private final SocialSignupTokenService socialSignupTokenService;
	
	private final PasswordHasher passwordHasher;
	
	private final AuthTokenService authTokenService;
	
	private final Clock clock;
	
	/*
	 * 이메일 회원가입
	 * - 인증 완료 토큰 사용과 계정 생성을 한 트랜잭션에서 처리
	 * - 가입에 실패하면 토큰 소모 X
	 * - 입력값 검증과 BCrypt 해시는 토큰 행 잠그기 전에 끝남
	 */
	@Transactional
	public AuthResult signupWithEmail(String password, String nickname, String verificationToken) {
		PasswordPolicy.validate(password);
		PublicIdentifier publicIdentifier = generatePublicIdentifier(nickname);
		String passwordHash = passwordHasher.hash(password);
		
		VerifiedEmail verified = consumeSignupVerification(verificationToken);
		
		Instant now = Instant.now(clock);
		
		Account account = Account.register(publicIdentifier, verified.email(), now, verified.verifiedAt());
		account.registerPassword(passwordHash, now);
		
		return saveAndIssueTokens(account);
	}
	
	/*
	 * 카카오 회원가입 완료
	 * - signupToken, verificationToken 사용과 계정 생성을 한 트랜잭션에서 처리
	 * - 가입에 실패하면 두 토큰 모두 소모 X
	 * - 비밀번호 없이 만들고 카카오 계정을 연결한다 (소셜 전용 계정)
	 */
	@Transactional
	public AuthResult signupWithKakao(String signupToken, String verificationToken, String nickname) {
		PublicIdentifier publicIdentifier = generatePublicIdentifier(nickname);
		
		SocialIdentity identity = socialSignupTokenService.consume(signupToken);
		VerifiedEmail verified = consumeSignupVerification(verificationToken);
		
		// 로그인에서 signupToken을 받은 뒤 같은 카카오 계정이 먼저 가입했을 수 있다.
		if (accountRepository.findBySocialAccount(identity.provider(), identity.providerUserId()).isPresent()) {
			throw new AccountException(AccountErrorCode.SOCIAL_IDENTITY_IN_USE);
		}
		
		Instant now = Instant.now(clock);
		
		Account account = Account.register(publicIdentifier, verified.email(), now, verified.verifiedAt());
		account.connectSocialAccount(identity.provider(), identity.providerUserId(), now);
		
		return saveAndIssueTokens(account);
	}
	
	// 가입 목적의 인증 완료 토큰을 사용하고, 그 이메일이 이미 쓰이는지 확인 (MANDATORY)
	private VerifiedEmail consumeSignupVerification(String verificationToken) {
		VerifiedEmail verified = emailVerificationService.consumeToken(
				verificationToken,
				EmailVerificationPurpose.SIGN_UP
		);
		
		if (accountRepository.existsNonWithdrawnByEmail(verified.email())) {
			throw new AccountException(AccountErrorCode.EMAIL_ALREADY_IN_USE);
		}
		
		return verified;
	}
	
	// 바로 INSERT해 동시 가입의 유니크 위반을 여기서 확인한 뒤 토큰 발급
	private AuthResult saveAndIssueTokens(Account account) {
		Account saved = accountRepository.saveAndFlush(account);
		
		return authTokenService.issue(saved);
	}
	
	private PublicIdentifier generatePublicIdentifier(String nickname) {
		for (int attempt = 0; attempt < MAX_TAG_ATTEMPTS; attempt++) {
			PublicIdentifier candidate = PublicIdentifier.withGeneratedTag(nickname, random);
			
			if (!accountRepository.existsByPublicIdentifier(candidate)) {
				return candidate;
			}
		}
		
		throw new IllegalStateException("사용 가능한 태그를 생성하지 못했습니다.");
	}
}