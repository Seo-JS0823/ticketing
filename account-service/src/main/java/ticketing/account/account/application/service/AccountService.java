package ticketing.account.account.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.properties.AccountProperties;
import ticketing.account.account.application.result.MyAccountResult;
import ticketing.account.account.application.result.WithdrawalResult;
import ticketing.account.account.domain.model.Account;
import ticketing.account.auth.application.service.TokenService;
import ticketing.account.verification.application.result.VerifiedEmail;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final LoginAccountReader loginAccountReader;
	
	private final ReauthenticationService reauthenticationService;
	
	private final TokenService tokenService;
	
	private final AccountProperties accountProperties;
	
	private final Clock clock;
	
	@Transactional(readOnly = true)
	public MyAccountResult getMyAccount(UUID accountId) {
		return MyAccountResult.from(loginAccountReader.getLoginAccount(accountId));
	}
	
	@Transactional
	public WithdrawalResult withdraw(
			UUID accountId,
			String currentPassword,
			String verificationToken,
			String clientIp
	) {
		Account account = loginAccountReader.getLoginAccount(accountId);
		
		boolean passwordReauthentication = account.hasPassword();
		
		if (passwordReauthentication) {
			reauthenticationService.verifyPassword(account, currentPassword, clientIp);
		}
		else {
			VerifiedEmail verified = reauthenticationService.consumeSensitiveActionToken(verificationToken);
			
			reauthenticationService.validateTokenOwner(account, verified);
		}
		
		account.withdraw(Instant.now(clock), accountProperties.recoveryPeriod());
		
		WithdrawalResult result = WithdrawalResult.from(account);
		
		tokenService.revokeAll(account.getId());
		
		if (passwordReauthentication) {
			reauthenticationService.recordPasswordSuccess(account, clientIp);
		}
		
		return result;
	}
}
