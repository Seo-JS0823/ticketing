package ticketing.account.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ticketing.account.account.application.properties.KakaoProperties;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoConfig {

	@Bean
	RestClient kakaoRestClient(KakaoProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		
		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}
}
