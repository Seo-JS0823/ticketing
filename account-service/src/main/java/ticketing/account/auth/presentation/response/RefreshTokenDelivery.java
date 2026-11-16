package ticketing.account.auth.presentation.response;

import ticketing.account.auth.presentation.request.ClientType;

public enum RefreshTokenDelivery {

	COOKIE,
	
	BODY;
	
	public static RefreshTokenDelivery from(ClientType clientType) {
		return clientType == ClientType.APP
				? BODY
				: COOKIE;
	}
	
	public boolean inBody() {
		return this == BODY;
	}
}
