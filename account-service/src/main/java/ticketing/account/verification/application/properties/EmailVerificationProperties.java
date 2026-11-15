package ticketing.account.verification.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
		@NotBlank @Email String mailFrom
) {

}
