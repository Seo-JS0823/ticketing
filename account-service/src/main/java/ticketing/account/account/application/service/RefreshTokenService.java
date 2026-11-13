package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.account.account.application.result.RefreshTokenResult;
import ticketing.account.account.application.util.VerificationCrypto;
import ticketing.account.account.domain.exception.AuthErrorCode;
import ticketing.account.account.domain.exception.AuthException;
import ticketing.account.account.domain.model.RefreshToken;
import ticketing.account.account.domain.repository.RefreshTokenRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	
	private final VerificationCrypto verificationCrypto;
	
	private final Clock clock;
	
	@Transactional
	public RefreshTokenResult issue(UUID accountId) {
		String token = verificationCrypto.generateToken();
		
		refreshTokenRepository.save(RefreshToken.issue(
				accountId,
				verificationCrypto.hash(token),
				Instant.now(clock)
		));
		
		return result(accountId, token);
	}
	
	/*
	 * 토큰 재발급
	 * 
	 * 받은 토큰을 사용 처리한 후 같은 세션의 새 토큰을 발급
	 * 이미 사용한 토큰이면 세션 전체 폐기후 거부
	 * 예외가 나도 폐기는 저장되어야 하므로 AuthException은 롤백하지 않음
	 */
	@Transactional(noRollbackFor = AuthException.class)
	public RefreshTokenResult rotate(String refreshToken) {
		RefreshToken current = findForUpdate(refreshToken);
		Instant now = Instant.now(clock);
		
		if (current.isUsed()) {
			refreshTokenRepository.revokeSession(current.getSessionId(), now);
			
			log.warn("이미 사용한 Refresh Token이 다시 들어와 세션을 폐기했습니다. sessionId={}", current.getSessionId());
			
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		
		String newToken = verificationCrypto.generateToken();
		
		refreshTokenRepository.save(current.rotate(verificationCrypto.hash(newToken), now));
		
		return result(current.getAccountId(), newToken);
	}
	
	@Transactional
	public void revoke(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		
		refreshTokenRepository
				.findByTokenHash(verificationCrypto.hash(refreshToken))
				.ifPresent(token -> refreshTokenRepository.revokeSession(token.getSessionId(), Instant.now(clock)));
	}
	
	@Transactional
	public void revokeAll(UUID accountId) {
		refreshTokenRepository.revokeAllByAccountId(accountId, Instant.now(clock));
	}
	
	private RefreshToken findForUpdate(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		
		return refreshTokenRepository.findByTokenHashForUpdate(verificationCrypto.hash(refreshToken))
				.orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
	}
	
	private RefreshTokenResult result(UUID accountId, String token) {
		return new RefreshTokenResult(
				accountId,
				token,
				RefreshToken.TTL.toSeconds()
		);
	}
}
