package ticketing.account.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GlobalResponse<T>(
		String code,
		String message,
		T data
) {
	
	public static <T> GlobalResponse<T> of(String code, T data) {
		return new GlobalResponse<>(code, null, data);
	}
	
	public static <T> GlobalResponse<T> of(T data) {
		return new GlobalResponse<>("SUCCESS", null, data);
	}
	
	public static GlobalResponse<Void> of(String code, String message) {
		return new GlobalResponse<>(code, message, null);
	}
	
	public static <T> GlobalResponse<T> of(String code, String message, T data) {
		return new GlobalResponse<>(code, message, data);
	}
}
