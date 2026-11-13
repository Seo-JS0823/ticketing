package ticketing.account.account.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import ticketing.account.account.domain.model.RefreshToken;

public interface RefreshTokenRepository {

	RefreshToken save(RefreshToken refreshToken);
	
	Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);
	
	Optional<RefreshToken> findByTokenHash(String tokenHash);
	
	void revokeSession(UUID sessionId, Instant now);
	
	void revokeAllByAccountId(UUID accountId, Instant now);
}
