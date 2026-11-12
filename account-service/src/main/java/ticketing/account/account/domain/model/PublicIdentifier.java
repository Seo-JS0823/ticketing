package ticketing.account.account.domain.model;

import java.text.Normalizer;
import java.util.random.RandomGenerator;
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
	
	private static final String NICKNAME_REGEX = "^[가-힣a-zA-Z0-9]{2,10}$";
	
	private static final String TAG_REGEX = "^[가-힣a-zA-Z0-9]{4,8}$";
	
	private static final Pattern NICKNAME_PATTERN = Pattern.compile(NICKNAME_REGEX);
	
	private static final Pattern TAG_PATTERN = Pattern.compile(TAG_REGEX);
	
	private static final String GENERATED_TAG_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	
	private static final int GENERATED_TAG_LENGTH = 8;
	
	@Column(nullable = false, length = 10)
	private final String nickname;
	
	@Column(nullable = false, length = 10)
	private final String tag;
	
	private PublicIdentifier(String nickname, String tag) {
		String normalizedNickname = normalize(nickname);
		String normalizedTag = normalize(tag);
		
		validateNickname(normalizedNickname);
		validateTag(normalizedTag);
		
		this.nickname = normalizedNickname;
		this.tag = normalizedTag;
	}
	
	public static PublicIdentifier of(String nickname, String tag) {
		return new PublicIdentifier(nickname, tag);
	}
	
	// 가입 시 태그 생성
	public static PublicIdentifier withGeneratedTag(String nickname, RandomGenerator random) {
		StringBuilder tag = new StringBuilder(GENERATED_TAG_LENGTH);
		
		for (int i = 0; i < GENERATED_TAG_LENGTH; i++) {
			tag.append(GENERATED_TAG_CHARACTERS.charAt(random.nextInt(GENERATED_TAG_CHARACTERS.length())));
		}
		
		return new PublicIdentifier(nickname, tag.toString());
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
	
	private static String normalize(String value) {
		return value == null
				? null
				: Normalizer.normalize(value, Normalizer.Form.NFC);
	}
}
