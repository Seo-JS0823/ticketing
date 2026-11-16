package ticketing.account.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.repository.AccountRepository;
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
	
	private final AccountRepository accountRepository;

	private final TokenCrypto tokenCrypto;

	private final Clock clock;
	
	@Value("${spring.application.name}")
	private String serviceName;

	@Transactional
	public AuthResult issue(Account account) {
		return authResult(account, issueRefreshToken(account.getId()));
	}
	
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
	
	@Transactional(noRollbackFor = AuthException.class)
	public AuthResult refresh(String refreshToken) {
		Rotation rotation = rotateInSession(refreshToken);
		
		Account account = accountRepository.findById(rotation.result().accountId())
				.orElseThrow(() -> revokeAndReject(rotation.sessionId(), AuthErrorCode.INVALID_REFRESH_TOKEN));
		
		if (account.getStatus() == AccountStatus.WITHDRAWN) {
			log.warn(
					"[{}] 탈퇴 계정의 Refresh Token 으로 재발급을 요청해 세션을 폐기했습니다. sessionId={}",
					serviceName,
					rotation.sessionId()
			);
			
			throw revokeAndReject(rotation.sessionId(), AuthErrorCode.ACCOUNT_WITHDRAWN);
		}
		
		return authResult(account, rotation.result());
	}
	
	@Transactional(noRollbackFor = AuthException.class)
	public RefreshTokenResult rotate(String refreshToken) {
		return rotateInSession(refreshToken).result();
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
	
	private Rotation rotateInSession(String refreshToken) {
		RefreshToken current = findForUpdate(refreshToken);
		Instant now = Instant.now(clock);
		
		if (current.isUsed()) {
			refreshTokenRepository.revokeSession(current.getSessionId(), now);
			
			log.warn(
					"[{}] 이미 사용한 Refresh Token이 다시 들어와 세션을 폐기했습니다. sessionId={}",
					serviceName,
					current.getSessionId()
			);
			
			throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		}
		
		String newToken = tokenCrypto.generateToken();
		
		refreshTokenRepository.save(current.rotate(tokenCrypto.hash(newToken), now));
		
		return new Rotation(
				current.getSessionId(),
				result(current.getAccountId(), newToken)
		);
	}
	
	@Transactional
	public void logout(UUID accountId, String... refreshTokens) {
		for (String refreshToken : refreshTokens) {
			revokeOwnSession(accountId, refreshToken);
		}
	}
	
	private void revokeOwnSession(UUID accountId, String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		
		refreshTokenRepository.findByTokenHash(tokenCrypto.hash(refreshToken))
		.ifPresent(token -> {
			if (!token.getAccountId().equals(accountId)) {
				log.warn(
						"[{}] 다른 사용자의 Refresh Token으로 로그아웃을 요청해 폐기하지 않았습니다. accountId={}, sessionId={}",
						serviceName,
						accountId,
						token.getSessionId()
				);
				
				return;
			}
			
			refreshTokenRepository.revokeSession(token.getSessionId(), Instant.now(clock));
		});
	}
	
	private AuthException revokeAndReject(UUID sessionId, AuthErrorCode errorCode) {
		refreshTokenRepository.revokeSession(sessionId, Instant.now(clock));
		
		return new AuthException(errorCode);
	}
	
	private AuthResult authResult(
			Account account,
			RefreshTokenResult refreshToken
	) {
		AccessTokenResult accessToken = accessTokenIssuer.issue(
				account.getId(),
				account.getRole(),
				account.getStatus()
		);
		
		return new AuthResult(accessToken, refreshToken, AccountSummary.from(account));
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
	
	private record Rotation(UUID sessionId, RefreshTokenResult result) {}
}
