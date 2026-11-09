package ticketing.account.account.application.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ticketing.account.account.application.dto.VerificationMailRequested;
import ticketing.account.account.application.port.VerificationMailSender;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationMailEventListener {

	private final VerificationMailSender verificationMailSender;
	
	@Async("mailTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(VerificationMailRequested mail) {
		try {
			verificationMailSender.send(mail);
		} catch (RuntimeException e) {
			log.error("인증 메일 발송 실패 verificationId={}", mail.verificationId(), e);
		}
	}
}
