package ticketing.account.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.account.account.domain.model.Account;
import ticketing.account.auth.application.port.AccessTokenIssuer;
import ticketing.account.auth.application.result.AccessTokenResult;
import ticketing.account.auth.application.result.AuthResult;
import ticketing.account.auth.application.result.AuthResult.AccountSummary;
import ticketing.account.auth.application.result.RefreshTokenResult;
import ticketing.account.auth.domain.exception.AuthErrorCode;
import ticketing.account.auth.domain.exception.AuthException;
import ticketing.account.auth.domain.model.RefreshToken;
import ticketing.account.auth.domain.repository.RefreshTokenRepository;
import ticketing.account.global.crypto.TokenCrypto;

/*
 * 로그인 세션 토큰
 *
 * - 새 로그인 세션의 Access Token과 Refresh Token 발급
 * - Refresh Token 교체(재발급), 폐기
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

	private final AccessTokenIssuer accessTokenIssuer;

	private final RefreshTokenRepository refreshTokenRepository;

	private final TokenCrypto tokenCrypto;

	private final Clock clock;

	/*
	 * 새 로그인 세션 발급
	 *
	 * 같은 클래스의 issueRefreshToken을 직접 부르면 프록시를 거치지 않아
	 * 그 메서드의 @Transactional이 적용되지 않으므로 이 메서드에 트랜잭션을 건다.
	 * 바깥 트랜잭션(가입)이 있으면 거기에 참여한다.
	 */
	@Transactional
	public AuthResult issue(Account account) {
		AccessTokenResult accessToken = accessTokenIssuer.issue(
				account.getId(),
				account.getRole(),
				account.getStatus()
		);

		RefreshTokenResult refreshToken = issueRefreshToken(account.getId());

		return new AuthResult(accessToken, refreshToken, AccountSummary.from(account));
	}

	// 새 로그인 세션의 첫 Refresh Token 발급
	@Transactional
	public RefreshTokenResult issueRefreshToken(UUID accountId) {
		String token = tokenCrypto.generateToken();
		
		refreshTokenRepository.save(RefreshToken.issue(
				accountId,
				tokenCrypto.hash(token),
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
		
		String newToken = tokenCrypto.generateToken();
		
		refreshTokenRepository.save(current.rotate(tokenCrypto.hash(newToken), now));
		
		return result(current.getAccountId(), newToken);
	}
	
	@Transactional
	public void revoke(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		
		refreshTokenRepository
				.findByTokenHash(tokenCrypto.hash(refreshToken))
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
		
		return refreshTokenRepository.findByTokenHashForUpdate(tokenCrypto.hash(refreshToken))
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
