package ticketing.account.account.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;

public interface AccountRepository {

	Account save(Account account);
	
	Account saveAndFlush(Account account);
	
	Optional<Account> findById(UUID accountId);
	
	Optional<Account> findNonWithdrawnByEmail(Email email);
	
	List<Account> findWithdrawnByEmail(Email email);
	
	Optional<Account> findBySocialAccount(SocialProvider provider, String providerUserId);
	
	boolean existsNonWithdrawnByEmail(Email email);
	
	boolean existsByPublicIdentifier(PublicIdentifier publicIdentifier);
}
