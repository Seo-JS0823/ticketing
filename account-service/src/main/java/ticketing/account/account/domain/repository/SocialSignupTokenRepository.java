package ticketing.account.account.domain.repository;

import java.util.Optional;

import ticketing.account.account.domain.model.SocialSignupToken;

public interface SocialSignupTokenRepository {

	SocialSignupToken save(SocialSignupToken token);
	
	Optional<SocialSignupToken> findByTokenHashForUpdate(String tokenHash);
}
