package ticketing.account.auth.application.result;

import ticketing.account.account.domain.model.SocialProvider;

public record SocialIdentity(
		SocialProvider provider,
		String providerUserId
) {

}
