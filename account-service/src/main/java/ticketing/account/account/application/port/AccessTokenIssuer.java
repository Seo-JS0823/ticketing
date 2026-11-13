package ticketing.account.account.application.port;

import java.util.UUID;

import ticketing.account.account.application.result.AccessTokenResult;
import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;

public interface AccessTokenIssuer {

	AccessTokenResult issue(UUID accountId, AccountRole role, AccountStatus status);
}
