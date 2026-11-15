package ticketing.account.global.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/*
 * 인증 코드, 불투명 토큰 생성과 HMAC-SHA256 해시
 *
 * 이메일 인증, Refresh Token, 카카오 가입 토큰, 로그인 실패 제한에서 함께 사용
 */
@Component
public class TokenCrypto {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	
	private static final int CODE_BOUND = 1_000_000;
	
	private static final int TOKEN_BYTES = 32;
	
	private final SecureRandom secureRandom = new SecureRandom();
	
	private final SecretKeySpec hmacKey;
	
	public TokenCrypto(CryptoProperties properties) {
		this.hmacKey = new SecretKeySpec(
				properties.hmacSecret().getBytes(StandardCharsets.UTF_8),
				HMAC_ALGORITHM
		);
	}
	
	// 000000 ~ 999999
	public String generateCode() {
		return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
	}
	
	// URL 안전 불투명 토큰
	public String generateToken() {
		byte[] token = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(token);
		
		return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
	}
	
	// 소문자 hex 64자
	public String hash(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(hmacKey);
			
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC 계산에 실패했습니다.", e);
		}
	}
}