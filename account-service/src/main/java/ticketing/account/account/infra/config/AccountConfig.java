package ticketing.account.account.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import ticketing.account.account.application.properties.AccountProperties;

@Configuration
@EnableConfigurationProperties(AccountProperties.class)
public class AccountConfig {

}
