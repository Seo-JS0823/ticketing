package ticketing.account.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;
import ticketing.account.global.exception.CommonErrorCode;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final SecurityErrorResponseWriter errorResponseWriter;
	
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, e) -> errorResponseWriter.write(request, response, CommonErrorCode.UNAUTHORIZED))
						.accessDeniedHandler((request, response, e) -> errorResponseWriter.write(request, response, CommonErrorCode.FORBIDDEN))
				)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/error", "/actuator/health").permitAll()
						
						.requestMatchers("/api/v1/email-verifications/**").permitAll()
						
						.requestMatchers(
								HttpMethod.POST,
								"/api/v1/accounts",
								"/api/v1/accounts/kakao"
						).permitAll()
						
						.requestMatchers(
								HttpMethod.POST,
								"/api/v1/auth/login",
								"/api/v1/auth/kakao",
								"/api/v1/auth/refresh",
								"/api/v1/auth/password-reset"
						).permitAll()
						
						.requestMatchers("/api/v1/account-recoveries/**").permitAll()
						
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						
						.requestMatchers("/internal/**").denyAll()
						
						.anyRequest().authenticated()
				)
		;
		
		return http.build();
	}
}
