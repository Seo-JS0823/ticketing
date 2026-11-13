package ticketing.account.account.application.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.port.AccessTokenIssuer;
import ticketing.account.account.application.port.PasswordHasher;
import ticketing.account.account.application.result.AccessTokenResult;
import ticketing.account.account.application.result.AuthResult;
import ticketing.account.account.application.result.AuthResult.AccountSummary;
import ticketing.account.account.application.result.RefreshTokenResult;
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
	
	private final PasswordHasher passwordHasher;
	
	private final AccessTokenIssuer accessTokenIssuer;
	
	private final RefreshTokenService refreshTokenService;
	
	private final Clock clock;
	
	/*
	 * 이메일 회원가입
	 * - 인증 완료 토큰 사용과 계정 생성을 한 트랜잭션에서 처리
	 * - 가입에 실패하면 토큰 소모 X
	 * - 입력값 검증과 BCrypt 해시는 토큰 행 잠그기 전에 끝남
	 */
	public AuthResult signupWithEmail(String password, String nickname, String verificationToken) {
		PasswordPolicy.validate(password);
		PublicIdentifier publicIdentifier = generatePublicIdentifier(nickname);
		String passwordHash = passwordHasher.hash(password);
		
		// consumeToken - MANDATORY
		VerifiedEmail verified = emailVerificationService.consumeToken(
				verificationToken,
				EmailVerificationPurpose.SIGN_UP
		);
		
		if (accountRepository.existsNonWithdrawnByEmail(verified.email())) {
			throw new AccountException(AccountErrorCode.EMAIL_ALREADY_IN_USE);
		}
		
		Instant now = Instant.now(clock);
		
		Account account = Account.register(
				publicIdentifier,
				verified.email(),
				now,
				verified.verifiedAt()
		);
		account.registerPassword(passwordHash, now);
		
		Account saved = accountRepository.saveAndFlush(account);
		
		AccessTokenResult accessToken = accessTokenIssuer.issue(
				saved.getId(),
				saved.getRole(),
				saved.getStatus()
		);
		
		RefreshTokenResult refreshToken = refreshTokenService.issue(saved.getId());
		
		return new AuthResult(
				accessToken,
				refreshToken,
				AccountSummary.from(saved)
		);
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
