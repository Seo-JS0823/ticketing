package ticketing.account.account.infra.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import ticketing.account.TestcontainersConfiguration;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.Email;
import ticketing.account.account.domain.model.PublicIdentifier;
import ticketing.account.account.domain.model.SocialProvider;
import ticketing.account.account.domain.repository.AccountRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Sql(
		scripts = "/sql/clean-database.sql",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
public class AccountRepositoryAdapterTest {

	private static final String KAKAO_USER_ID = "1234567890";
	
	@Autowired
	private AccountRepository accountRepository;
	
	@Test
	@DisplayName("이미 쓰이는 이메일로 저장하면 EMAIL_ALREADY_IN_USE")
	void accountRepositoryAdapter_test1() {
		accountRepository.saveAndFlush(account("dup@example.com", "FIRST001"));
		
		assertAccountError(
				() -> accountRepository.saveAndFlush(account("dup@example.com", "SECOND01")),
				AccountErrorCode.EMAIL_ALREADY_IN_USE
		);
	}
	
	@Test
	@DisplayName("이미 다른 계정에 연결된 카카오 계정으로 저장하면 SOCIAL_IDENTITY_IN_USE")
	void accountRepositoryAdapter_test2() {
		accountRepository.saveAndFlush(kakaoAccount("first@example.com", "FIRST001"));
		
		assertAccountError(
				() -> accountRepository.saveAndFlush(kakaoAccount("second@example.com", "SECOND01")),
				AccountErrorCode.SOCIAL_IDENTITY_IN_USE
		);
	}
	
	private Account account(String email, String tag) {
		Instant now = Instant.now();
		
		return Account.register(PublicIdentifier.of("티켓러", tag), Email.of(email), now, now);
	}
	
	private Account kakaoAccount(String email, String tag) {
		Account account = account(email, tag);
		account.connectSocialAccount(SocialProvider.KAKAO, KAKAO_USER_ID, Instant.now());
		
		return account;
	}
	
	private void assertAccountError(ThrowingCallable callable, AccountErrorCode errorCode) {
		assertThatThrownBy(callable)
				.isInstanceOf(AccountException.class)
				.extracting(e -> ((AccountException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}