package ticketing.account.account.domain.model;

import java.util.regex.Pattern;

import jakarta.persistence.Column;
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
public class PublicIdentifier {
	
	private static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{1,8}$";
	
	private static final String TAG_REGEX = "^[a-zA-Z0-9]{1,8}$";
	
	private static final Pattern NICKNAME_PATTERN = Pattern.compile(NICKNAME_REGEX);
	
	private static final Pattern TAG_PATTERN = Pattern.compile(TAG_REGEX);
	
	@Column(nullable = false, length = 10)
	private final String nickname;
	
	@Column(nullable = false, length = 10)
	private final String tag;
	
	private PublicIdentifier(String nickname, String tag) {
		validateNickname(nickname);
		validateTag(tag);
		this.nickname = nickname;
		this.tag = tag;
	}
	
	public static PublicIdentifier of(String nickname, String tag) {
		return new PublicIdentifier(nickname, tag);
	}
	
	private void validateNickname(String nickname) {
		if (nickname == null || !NICKNAME_PATTERN.matcher(nickname).matches()) {
			throw new AccountException(AccountInvalidErrorCode.INVALID_NICKNAME_PATTERN);
		}
	}
	
	private void validateTag(String tag) {
		if (tag == null || !TAG_PATTERN.matcher(tag).matches()) {
			throw new AccountException(AccountInvalidErrorCode.INVALID_TAG_PATTERN);
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s#%s", nickname, tag);
	}
}
