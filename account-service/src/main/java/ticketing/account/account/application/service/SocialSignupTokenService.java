package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.SocialIdentity;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.model.SocialSignupToken;
import ticketing.account.account.domain.repository.SocialSignupTokenRepository;

@Service
@RequiredArgsConstructor
public class SocialSignupTokenService {
	
	private final SocialSignupTokenRepository socialSignupTokenRepository;
	
	private final VerificationCrypto verificationCrypto;
	
	private final Clock clock;
	
	/*
	 * 소셜 로그인에서 연결된 계정이 없을 때 발급
	 * DB에는 해시만 저장
	 */
	@Transactional
	public String issue(SocialProvider provider, String providerUserId, String providerEmail) {
		String token = verificationCrypto.generateToken();
		
		socialSignupTokenRepository.save(SocialSignupToken.issue(
				provider,
				providerUserId,
				providerEmail,
				verificationCrypto.hash(token),
				Instant.now(clock)
		));
		
		return token;
	}
	
	/*
	 * 소셜 회원가입 완료에서 사용
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public SocialIdentity consume(String token) {
		if (token == null || token.isBlank()) {
			throw new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
		}
		
		SocialSignupToken signupToken = socialSignupTokenRepository
				.findByTokenHashForUpdate(verificationCrypto.hash(token))
				.orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
		
		signupToken.consume(Instant.now(clock));
		
		return new SocialIdentity(
				signupToken.getProvider(),
				signupToken.getProviderUserId()
		);
	}
}
