package br.ufba.veridt.shared.error.handler;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.ufba.veridt.shared.error.dto.ErrorResponse;
import br.ufba.veridt.shared.error.dto.FieldErrorResponse;
import br.ufba.veridt.shared.error.exception.BusinessException;
import br.ufba.veridt.shared.error.exception.ConflictException;
import br.ufba.veridt.shared.error.exception.ResourceNotFoundException;
import br.ufba.veridt.shared.error.exception.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

/** Converte exceções em {@link ErrorResponse}. A herança mantém o status correto dos erros do Spring MVC. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String DADOS_INVALIDOS = "DADOS_INVALIDOS";
	private static final String REQUISICAO_INVALIDA = "REQUISICAO_INVALIDA";
	private static final String ERRO_INTERNO = "ERRO_INTERNO";

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
		return businessError(HttpStatus.NOT_FOUND, ex);
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
		return businessError(HttpStatus.CONFLICT, ex);
	}

	@ExceptionHandler(UnauthorizedException.class)
	ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
		return businessError(HttpStatus.UNAUTHORIZED, ex);
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
		return businessError(HttpStatus.UNPROCESSABLE_CONTENT, ex);
	}

	/** Validação de parâmetros de services ({@code @Validated}), que não passa pelo Spring MVC. */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		List<FieldErrorResponse> errors = ex.getConstraintViolations().stream()
			.map(violation -> new FieldErrorResponse(lastNode(violation.getPropertyPath()), violation.getMessage()))
			.toList();
		HttpStatus status = HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status)
			.body(ErrorResponse.of(status.value(), DADOS_INVALIDOS, "Dados inválidos.", errors));
	}

	/** Relança para o Spring Security responder 401 ou 403, em vez de cair no erro 500 abaixo. */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void handleSecurity(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		logger.error("Erro inesperado ao processar a requisição", ex);
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		ErrorResponse body = ErrorResponse.of(status.value(), ERRO_INTERNO, "Erro interno. Tente novamente mais tarde.");
		return ResponseEntity.status(status).body(body);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
			.map(GlobalExceptionHandler::toFieldError)
			.toList();
		ErrorResponse body = ErrorResponse.of(status.value(), DADOS_INVALIDOS, "Dados inválidos.", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		Object response = body instanceof ErrorResponse ? body : ErrorResponse.of(statusCode.value(),
				REQUISICAO_INVALIDA, "Não foi possível processar a requisição.");
		return super.handleExceptionInternal(ex, response, headers, statusCode, request);
	}

	private static ResponseEntity<ErrorResponse> businessError(HttpStatus status, BusinessException ex) {
		ErrorResponse body = ErrorResponse.of(status.value(), ex.getCode(), ex.getMessage());
		return ResponseEntity.status(status).body(body);
	}

	/** De "register.request.email" fica só "email", o nome do campo que o cliente enviou. */
	private static String lastNode(Path path) {
		String field = null;
		for (Path.Node node : path) {
			field = node.getName();
		}
		return field;
	}

	private static FieldErrorResponse toFieldError(FieldError error) {
		return new FieldErrorResponse(error.getField(), error.getDefaultMessage());
	}

}
