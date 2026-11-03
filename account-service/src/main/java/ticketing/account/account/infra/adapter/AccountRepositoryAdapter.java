package ticketing.account.account.infra.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.account.infra.persistence.JpaAccountRepository;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

	private final JpaAccountRepository jpaAccountRepository;

	@Override
	public Account save(Account account) {
		return jpaAccountRepository.save(account);
	}

	@Override
	public Optional<Account> findById(UUID accountId) {
		return jpaAccountRepository.findById(accountId);
	}

	@Override
	public Optional<Account> findByEmail(Email email) {
		return jpaAccountRepository.findByEmail(email);
	}

	@Override
	public Optional<Account> findBySocialAccount(SocialProvider provider, String providerUserId) {
		return jpaAccountRepository.findBySocialAccount(provider, providerUserId);
	}

	@Override
	public boolean existsActiveByEmail(Email email) {
		return jpaAccountRepository.existsByEmailAndStatus(email, AccountStatus.ACTIVE);
	}

	@Override
	public boolean existsByPublicIdentifier(PublicIdentifier publicIdentifier) {
		return jpaAccountRepository.existsByPublicIdentifier(publicIdentifier);
	}
	
	
}
