package ticketing.account.account.infra.adapter;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.domain.model.RefreshToken;
import ticketing.account.account.domain.repository.RefreshTokenRepository;
import ticketing.account.account.infra.persistence.JpaRefreshTokenRepository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

	private final JpaRefreshTokenRepository jpaRefreshTokenRepository;

	@Override
	public RefreshToken save(RefreshToken refreshToken) {
		return jpaRefreshTokenRepository.save(refreshToken);
	}

	@Override
	public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
		return jpaRefreshTokenRepository.findByTokenHashForUpdate(tokenHash);
	}

	@Override
	public Optional<RefreshToken> findByTokenHash(String tokenHash) {
		return jpaRefreshTokenRepository.findByTokenHash(tokenHash);
	}

	@Override
	public void revokeSession(UUID sessionId, Instant now) {
		jpaRefreshTokenRepository.revokeBySessionId(sessionId, now);
	}

	@Override
	public void revokeAllByAccountId(UUID accountId, Instant now) {
		jpaRefreshTokenRepository.revokeByAccountId(accountId, now);
	}
	
	
}
