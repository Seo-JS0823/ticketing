package ticketing.account.account.infra.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
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

	private static final String EMAIL_UNIQUE_INDEX = "uk_acounts_email_not_withdrwan";
	
	private final JpaAccountRepository jpaAccountRepository;

	@Override
	public Account save(Account account) {
		return jpaAccountRepository.save(account);
	}

	@Override
	public Account saveAndFlush(Account account) {
		try {
			return jpaAccountRepository.saveAndFlush(account);
		} catch (DataIntegrityViolationException e) {
			if (isViolated(e, EMAIL_UNIQUE_INDEX)) {
				throw new AccountException(AccountErrorCode.EMAIL_ALREADY_IN_USE);				
			}
			
			throw e;
		}
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
		return jpaAccountRepository.findByEmailAndStatusOrderByWithdrawnAtDesc(email, AccountStatus.WITHDRAWN);
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
	
	private static boolean isViolated(DataIntegrityViolationException e, String constraintName) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& constraintName.equals(violation.getConstraintName());
	}
	
}
