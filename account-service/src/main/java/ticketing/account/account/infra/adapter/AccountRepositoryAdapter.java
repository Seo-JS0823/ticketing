package ticketing.account.account.infra.adapter;

import java.util.List;
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
	public Optional<Account> findNonWithdrawnByEmail(Email email) {
		return jpaAccountRepository.findByEmailAndStatusNot(email, AccountStatus.WITHDRAWN);
	}

	@Override
	public Optional<Account> findBySocialAccount(SocialProvider provider, String providerUserId) {
		return jpaAccountRepository.findBySocialAccount(provider, providerUserId);
	}

	@Override
	public List<Account> findWithdrawnByEmail(Email email) {
		return jpaAccountRepository.findByEmailAndStatusOrderByWithdrwanAtDesc(email, AccountStatus.WITHDRAWN);
	}

	@Override
	public boolean existsNonWithdrawnByEmail(Email email) {
		return jpaAccountRepository.existsByEmailAndStatusNot(email, AccountStatus.WITHDRAWN);
	}

	@Override
	public boolean existsByPublicIdentifier(PublicIdentifier publicIdentifier) {
		return jpaAccountRepository.existsByPublicIdentifierIgnoreCase(
				publicIdentifier.getNickname(),
				publicIdentifier.getTag()
		);
	}
	
}
