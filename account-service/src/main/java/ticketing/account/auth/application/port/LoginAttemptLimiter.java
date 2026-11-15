package ticketing.account.auth.application.port;

public interface LoginAttemptLimiter {

	// 실패 한도 체크
	void checkAllowed(String email, String clientIp);
	
	// 로그인 실패시 호출: 이메일+IP 카운터 / 이메일 카운터 1증가
	void recordFailure(String email, String clientIp);
	
	// 로그인 성공시 호출: 이메일+IP 카운터 지우고 이메일 카운터 유지
	void recordSuccess(String email, String clientIp);
}
