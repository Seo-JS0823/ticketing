package ticketing.account.account.infra.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.account.account.domain.model.Account;

public interface JpaAccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByEmail(String email);

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
			@Param("provider") String provider,
			@Param("providerUserId") String providerUserId
	);

	boolean existsByEmailAndStatus(String email, String name);

	boolean existsByNicknameAndTag(String nickname, String tag);

}
