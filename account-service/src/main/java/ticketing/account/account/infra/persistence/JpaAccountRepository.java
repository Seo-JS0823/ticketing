package ticketing.account.account.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;

public interface JpaAccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByEmail(Email email);

	@Query(
			"""
				SELECT a
				FROM Account a
				JOIN a.socialAccounts s
				WHERE s.provider = :provider
					AND s.providerUserId = :providerUserId
			"""
	)
	Optional<Account> findBySocialAccount(
			@Param("provider") SocialProvider provider,
			@Param("providerUserId") String providerUserId
	);

	boolean existsByEmailAndStatus(Email email, AccountStatus status);

	boolean existsByPublicIdentifier(PublicIdentifier publicIdentifier);

}
