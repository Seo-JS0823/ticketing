package ticketing.account.account.application.port;

import ticketing.account.account.application.dto.VerificationMailRequested;

public interface VerificationMailSender {

	void send(VerificationMailRequested mail);
}
