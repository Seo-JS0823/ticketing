package ticketing.account.account.infra.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ticketing.account.account.application.VerificationMailType;
import ticketing.account.account.application.dto.VerificationMailRequested;
import ticketing.account.account.application.port.VerificationMailSender;
import ticketing.account.account.application.properties.EmailVerificationProperties;

@Component
@RequiredArgsConstructor
public class SmtpVerificationMailSender implements VerificationMailSender {
	
	private final JavaMailSender javaMailSender;
	
	private final EmailVerificationProperties properties;
	
	@Override
	public void send(VerificationMailRequested mail) {
		SimpleMailMessage message = new SimpleMailMessage();
		
		message.setFrom(properties.mailFrom());
		message.setTo(mail.to());
		
		if (mail.type() == VerificationMailType.ALREADY_REGISTERED) {
			message.setSubject("[Ticketing] 이미 가입된 이메일입니다.");
			message.setText(
					"""
					이 이메일로 가입된 계정이 있습니다.
					기존 계정으로 로그인해 주세요.
					
					본인이 요청하지 않았다면 이 메일을 무시해 주세요.
					"""
			);
		} else {
			message.setSubject("[Ticketing] 이메일 인증 코드");
			message.setText(
					"""
					인증 코드: %s
					
					이 코드는 10분 동안 유효합니다.
					본인이 요청하지 않았다면 이 메일을 무시해 주세요.
					""".formatted(mail.code())
			);
		}
		
		javaMailSender.send(message);
	}

	
}
