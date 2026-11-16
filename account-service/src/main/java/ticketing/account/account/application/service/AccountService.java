package ticketing.account.account.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.result.MyAccountResult;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.model.Account;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.account.domain.repository.AccountRepository;
import ticketing.account.global.exception.CommonErrorCode;
import ticketing.account.global.exception.DomainException;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepository;
	
	@Transactional(readOnly = true)
	public MyAccountResult getMyAccount(UUID accountId) {
		return MyAccountResult.from(getLoginAccount(accountId));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Account getLoginAccount(UUID accountId) {
		if (accountId == null) {
			throw new DomainException(CommonErrorCode.UNAUTHORIZED);
		}
		
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new DomainException(CommonErrorCode.UNAUTHORIZED));
		
		if (account.getStatus() == AccountStatus.WITHDRAWN) {
			throw new AccountException(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
		}
		
		return account;
	}
}
