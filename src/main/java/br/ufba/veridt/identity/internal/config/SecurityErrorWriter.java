package br.ufba.veridt.identity.internal.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import br.ufba.veridt.shared.error.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Escreve erros da cadeia de segurança no mesmo formato do {@code GlobalExceptionHandler}. */
@Component
public class SecurityErrorWriter {

	private final JsonMapper jsonMapper;

	public SecurityErrorWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status.value(), code, message));
	}

}
