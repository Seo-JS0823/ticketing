package ticketing.account.global.exception;

public class DomainException extends RuntimeException {
	
	private DomainErrorCode errorCode;
	
	public DomainException(DomainErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
	
	public DomainErrorCode getErrorCode() {
		return this.errorCode;
	}
}
