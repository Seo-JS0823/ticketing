package ticketing.account.account.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.SocialProvider;

public interface JpaAccountRepository extends JpaRepository<Account, UUID> {

	@Query(
			"""
			SELECT a
			FROM Account a
			LEFT JOIN FETCH a.credential
			WHERE a.email = :email
				AND a.status <> :status
			"""
	)
	Optional<Account> findWithCredentialByEmailAndStatusNot(
			@Param("email") Email email,
			@Param("status") AccountStatus status
	);
	
	@Query(
			"""
			SELECT a
			FROM Account a
			LEFT JOIN FETCH a.credential
			WHERE a.email = :email
				AND a.status = :status
			ORDER BY a.withdrawnAt DESC
			"""
	)
	List<Account> findAllWithCredentialByEmailAndStatusOrderByWithdrawnAtDesc(
			Email email,
			AccountStatus status
	);
	
	@Query(
			"""
			SELECT a
			FROM Account a
			JOIN a.socialAccounts s
			WHERE s.provider = :provider
				AND s.providerUserId = :providerUserId
				AND s.deletedAt IS NULL
			"""
	)
	Optional<Account> findBySocialAccount(
			@Param("provider") SocialProvider provider,
			@Param("providerUserId") String providerUserId
	);

	boolean existsByEmailAndStatusNot(Email email, AccountStatus status);
	
	@Query(
			"""
			SELECT COUNT(a) > 0
			FROM Account a
			WHERE LOWER(a.publicIdentifier.nickname) = LOWER(:nickname)
				AND LOWER(a.publicIdentifier.tag) = LOWER(:tag)
			"""
	)
	boolean existsByPublicIdentifierIgnoreCase(
			@Param("nickname") String nickname,
			@Param("tag") String tag
	);
	
}
