package ticketing.account.auth.domain.repository;

import java.util.Optional;

import ticketing.account.auth.domain.model.SocialSignupToken;

public interface SocialSignupTokenRepository {

	SocialSignupToken save(SocialSignupToken token);
	
	Optional<SocialSignupToken> findByTokenHashForUpdate(String tokenHash);
}
