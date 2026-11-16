package ticketing.account.account.application.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.account")
public record AccountProperties(
		@NotNull Duration recoveryPeriod
) {

}
