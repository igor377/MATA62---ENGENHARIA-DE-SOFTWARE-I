package br.ufba.veridt.shared.error.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.shared.error.exception.BusinessException;
import br.ufba.veridt.shared.error.exception.ConflictException;
import br.ufba.veridt.shared.error.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;

class GlobalExceptionHandlerTests {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void regraDeNegocioRetorna422() throws Exception {
		mockMvc.perform(get("/negocio"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.code").value("SALDO_INSUFICIENTE"))
			.andExpect(jsonPath("$.message").value("Saldo insuficiente."))
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void recursoInexistenteRetorna404() throws Exception {
		mockMvc.perform(get("/nao-encontrado"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REGISTRO_NAO_ENCONTRADO"));
	}

	@Test
	void conflitoRetorna409() throws Exception {
		mockMvc.perform(get("/conflito"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_DUPLICADO"));
	}

	@Test
	void validacaoRetorna400ComOsCampos() throws Exception {
		mockMvc.perform(post("/validacao").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DADOS_INVALIDOS"))
			.andExpect(jsonPath("$.errors[0].field").value("nome"))
			.andExpect(jsonPath("$.errors[0].message").value("obrigatório"));
	}

	@Test
	void jsonMalformadoRetorna400Padronizado() throws Exception {
		mockMvc.perform(post("/validacao").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));
	}

	@Test
	void metodoNaoSuportadoMantemStatus405() throws Exception {
		mockMvc.perform(post("/negocio"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.status").value(405))
			.andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));
	}

	@Test
	void erroInesperadoRetorna500SemDetalhesInternos() throws Exception {
		mockMvc.perform(get("/erro"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("ERRO_INTERNO"))
			.andExpect(content().string(not(containsString("segredo"))));
	}

	@Test
	void validacaoDeServicoRetorna400ComOsCampos() throws Exception {
		mockMvc.perform(get("/validacao-servico"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DADOS_INVALIDOS"))
			.andExpect(jsonPath("$.errors[0].field").value("nome"))
			.andExpect(jsonPath("$.errors[0].message").value("obrigatório"));
	}

	@Test
	void excecaoDeSegurancaNaoECapturada() {
		assertThatThrownBy(() -> mockMvc.perform(get("/negado")))
			.hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	record TestRequest(@NotBlank(message = "obrigatório") String nome) {
	}

	@RestController
	static class TestController {

		@GetMapping("/negocio")
		void negocio() {
			throw new BusinessException("SALDO_INSUFICIENTE", "Saldo insuficiente.");
		}

		@GetMapping("/nao-encontrado")
		void naoEncontrado() {
			throw new ResourceNotFoundException("REGISTRO_NAO_ENCONTRADO", "Registro não encontrado.");
		}

		@GetMapping("/conflito")
		void conflito() {
			throw new ConflictException("EMAIL_DUPLICADO", "E-mail já cadastrado.");
		}

		@PostMapping("/validacao")
		void validacao(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/erro")
		void erro() {
			throw new IllegalStateException("segredo");
		}

		@GetMapping("/negado")
		void negado() {
			throw new AccessDeniedException("negado");
		}

		/** Simula a validação de parâmetros de um service anotado com {@code @Validated}. */
		@GetMapping("/validacao-servico")
		void validacaoServico() {
			try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
				throw new ConstraintViolationException(factory.getValidator().validate(new TestRequest("")));
			}
		}

	}

}
