package br.ufba.veridt.shared.error.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String code, String message,
		List<FieldErrorResponse> errors) {

	public static ErrorResponse of(int status, String code, String message) {
		return of(status, code, message, List.of());
	}

	public static ErrorResponse of(int status, String code, String message, List<FieldErrorResponse> errors) {
		return new ErrorResponse(Instant.now(), status, code, message, errors);
	}

}
