package ticketing.account.auth.application.port;

import java.util.UUID;

import ticketing.account.account.domain.model.AccountRole;
import ticketing.account.account.domain.model.AccountStatus;
import ticketing.account.auth.application.result.AccessTokenResult;

public interface AccessTokenIssuer {

	AccessTokenResult issue(UUID accountId, AccountRole role, AccountStatus status);
}
