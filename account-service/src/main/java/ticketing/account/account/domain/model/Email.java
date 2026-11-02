package ticketing.account.account.domain.model;

import java.util.regex.Pattern;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ticketing.account.account.domain.exception.AccountException;
import ticketing.account.account.domain.exception.AccountInvalidErrorCode;

@Getter
@EqualsAndHashCode
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
public class Email {
	
	private static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";
	
  private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
  
  private final String email;
  
  public Email(String email) {
  	validateEmail(email);
  	this.email = email;
  }
  
  private void validateEmail(String email) {
  	if (email == null || email.isBlank()) {
  		throw new AccountException(AccountInvalidErrorCode.EMAIL_REQUIRED);
  	}
  	
  	if (email.length() > 320) {
  		throw new AccountException(AccountInvalidErrorCode.EMAIL_TOO_LONG);
  	}
  	
  	if(!EMAIL_PATTERN.matcher(email).matches()) {
  		throw new AccountException(AccountInvalidErrorCode.INVALID_EMAIL_PATTERN);
  	}
  }
}
