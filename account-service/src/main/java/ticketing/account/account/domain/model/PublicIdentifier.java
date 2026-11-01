package ticketing.account.account.domain.model;

import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import ticketing.account.account.domain.exception.AccountErrorCode;
import ticketing.account.account.domain.exception.AccountException;

@Getter
@EqualsAndHashCode
public class PublicIdentifier {
	
	private static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{1,8}$";
	
	private static final String TAG_REGEX = "^[a-zA-Z0-9]{1,8}$";
	
	private static final Pattern NICKNAME_PATTERN = Pattern.compile(NICKNAME_REGEX);
	
	private static final Pattern TAG_PATTERN = Pattern.compile(TAG_REGEX);
	
	private final String nickname;
	
	private final String tag;
	
	public PublicIdentifier(String nickname, String tag) {
		validateNickname(nickname);
		validateTag(tag);
		this.nickname = nickname;
		this.tag = tag;
	}
	
	private void validateNickname(String nickname) {
		if (nickname == null || !NICKNAME_PATTERN.matcher(nickname).matches()) {
			throw new AccountException(AccountErrorCode.INVALID_NICKNAME_PATTERN);
		}
	}
	
	private void validateTag(String tag) {
		if (tag == null || !TAG_PATTERN.matcher(tag).matches()) {
			throw new AccountException(AccountErrorCode.INVALID_TAG_PATTERN);
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s#%s", nickname, tag);
	}
}
