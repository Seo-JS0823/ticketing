package ticketing.account.account.domain.repository;

import java.util.Optional;
import java.util.UUID;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;

public interface AccountRepository {

	Account save(Account account);
	
	Optional<Account> findById(UUID accountId);
	
	Optional<Account> findByEmail(Email email);
	
	Optional<Account> findBySocialAccount(SocialProvider provider, String providerUserId);
	
	boolean existsActiveByEmail(Email email);
	
	boolean existsByPublicIdentifier(PublicIdentifier publicIdentifier);
}
