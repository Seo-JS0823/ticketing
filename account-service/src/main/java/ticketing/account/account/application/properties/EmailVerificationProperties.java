package ticketing.account.account.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
		@NotBlank @Size(min = 32) String hmacSecret,
		@NotBlank @Email String mailFrom
) {

}
