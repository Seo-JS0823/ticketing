package ticketing.account.verification.application.port;

import ticketing.account.verification.application.event.VerificationMailRequested;

public interface VerificationMailSender {

	void send(VerificationMailRequested mail);
}
