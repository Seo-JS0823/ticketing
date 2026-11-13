package ticketing.account.account.infra.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import ticketing.account.account.application.port.PasswordHasher;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private final BCryptPasswordEncoder encoder;
	
	public BCryptPasswordHasher(
			@Value("${app.security.bcrypt-strength}") int strength
	) {
		this.encoder = new BCryptPasswordEncoder(strength);
	}
	
	@Override
	public String hash(String password) {
		return encoder.encode(password);
	}
	
	@Override
	public boolean matches(String rawPassword, String passwordHash) {
		return encoder.matches(rawPassword, passwordHash);
	}
}
