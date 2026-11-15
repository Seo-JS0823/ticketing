package ticketing.account.auth.infra.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import ticketing.account.auth.domain.model.SocialSignupToken;
import ticketing.account.auth.domain.repository.SocialSignupTokenRepository;
import ticketing.account.auth.infra.persistence.JpaSocialSignupTokenRepository;

@Repository
@RequiredArgsConstructor
public class SocialSignupTokenRepositoryAdapter implements SocialSignupTokenRepository {

	private final JpaSocialSignupTokenRepository jpaSocialSignupTokenRepository;

	@Override
	public SocialSignupToken save(SocialSignupToken token) {
		return jpaSocialSignupTokenRepository.save(token);
	}

	@Override
	public Optional<SocialSignupToken> findByTokenHashForUpdate(String tokenHash) {
		return jpaSocialSignupTokenRepository.findByTokenHashForUpdate(tokenHash);
	}
	
	
}
