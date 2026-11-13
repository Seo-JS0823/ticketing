package ticketing.account.account.application.port;

public interface PasswordHasher {

	String hash(String password);
	
	boolean matches(String rawPassword, String passwordHash);
}
