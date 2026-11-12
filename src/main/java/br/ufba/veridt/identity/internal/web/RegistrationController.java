package br.ufba.veridt.identity.internal.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufba.veridt.identity.internal.dto.RegisterUserRequest;
import br.ufba.veridt.identity.internal.dto.UserResponse;
import br.ufba.veridt.identity.internal.service.RegistrationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/register")
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
		return registrationService.register(request);
	}

}
