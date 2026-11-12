package ticketing.account.global.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		RSAKey rsaKey = new RSAKey.Builder(readPublicKey(properties.publicKey()))
				.privateKey(readPrivateKey(properties.privateKey()))
				.keyID(properties.keyId())
				.build();
		
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}
	
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(properties.publicKey()))
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		
		return decoder;
	}
	
	private static RSAPublicKey readPublicKey(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return RsaKeyConverters.x509().convert(in);
		} catch (IOException e) {
			throw new UncheckedIOException("JWT 공개키를 읽을 수 없습니다: " + resource, e);
		}
	}
	
	private static RSAPrivateKey readPrivateKey(Resource resource) {
		try (InputStream in = resource.getInputStream()){
			return RsaKeyConverters.pkcs8().convert(in);
		} catch (IOException e) {
			throw new UncheckedIOException("JWT 개인키를 읽을 수 없습니다: " + resource, e);
		}
	}
}
