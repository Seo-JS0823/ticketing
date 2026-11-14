package ticketing.account.account.application.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import ticketing.account.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class KakaoPropertiesTest {

	@Autowired
	private KakaoProperties kakaoProperties;
	
	@Test
	@DisplayName("설정 파일의 카카오 값이 바인딩된다")
	void kakaoProperties_test1() {
		assertThat(kakaoProperties.clientId()).isEqualTo("test-client-id");
		assertThat(kakaoProperties.appId()).isEqualTo(123456L);
		assertThat(kakaoProperties.allowedRedirectUris()).containsExactly("http://localhost:3000/oauth/kakao");
		assertThat(kakaoProperties.authBaseUrl()).isEqualTo("https://kauth.kakao.com");
		assertThat(kakaoProperties.apiBaseUrl()).isEqualTo("https://kapi.kakao.com");
		assertThat(kakaoProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
		assertThat(kakaoProperties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
	}
	
	@Test
	@DisplayName("허용 목록에 있는 redirectUri만 허용")
	void kakaoProperties_test2() {
		KakaoProperties properties = new KakaoProperties(
				"client-id",
				"client-secret",
				1L,
				List.of("http://localhost:3000/oauth/kakao", "https://ticketing.example.com/oauth/kakao"),
				"https://kauth.kakao.com",
				"https://kapi.kakao.com",
				Duration.ofSeconds(3),
				Duration.ofSeconds(5)
		);
		
		assertThat(properties.isAllowedRedirectUri("http://localhost:3000/oauth/kakao")).isTrue();
		assertThat(properties.isAllowedRedirectUri("https://ticketing.example.com/oauth/kakao")).isTrue();
		assertThat(properties.isAllowedRedirectUri("https://evil.example.com/oauth/kakao")).isFalse();
		assertThat(properties.isAllowedRedirectUri("http://localhost:3000/oauth/kakao/")).isFalse();
		assertThat(properties.isAllowedRedirectUri(null)).isFalse();
	}
}