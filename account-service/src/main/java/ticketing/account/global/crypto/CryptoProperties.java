package ticketing.account.global.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/*
 * TokenCrypto의 HMAC 비밀키
 *
 * 설정 키는 기존 app.email-verification.hmac-secret을 그대로 읽는다.
 * (yaml, 환경 변수 EMAIL_VERIFICATION_HMAC_SECRET 변경 없음)
 */
@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public record CryptoProperties(
		@NotBlank @Size(min = 32) String hmacSecret
) {

}
