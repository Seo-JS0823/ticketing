package ticketing.account.account.application;

public interface VerificationMailSender {

	void send(VerificationMailRequested mail);
}
