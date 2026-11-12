package br.ufba.veridt.identity.internal.validation;

import java.util.List;
import java.util.Objects;

import br.ufba.veridt.identity.internal.domain.OabRegistration;
import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.identity.internal.domain.UserType;
import br.ufba.veridt.identity.internal.dto.OabRegistrationRequest;
import br.ufba.veridt.identity.internal.dto.RegisterUserRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Regras entre as inscrições. O formato de cada uma é validado em {@link OabRegistrationRequest}. */
public class OabValidator implements ConstraintValidator<ValidOab, RegisterUserRequest> {

	private static final String FIELD = "oabRegistrations";

	@Override
	public boolean isValid(RegisterUserRequest request, ConstraintValidatorContext context) {
		if (request == null || request.type() == null) {
			return true;
		}
		List<OabRegistrationRequest> registrations = complete(request.oabRegistrations());
		boolean informed = request.oabRegistrations() != null && !request.oabRegistrations().isEmpty();
		if (request.type() != UserType.ADVOGADO) {
			return !informed || violation(context, "só pode ser informado por advogado");
		}
		if (!informed) {
			return violation(context, "obrigatório para advogado: informe ao menos uma inscrição");
		}
		if (hasRepeated(registrations)) {
			return violation(context, "não pode ter inscrições repetidas");
		}
		long principal = registrations.stream().filter(r -> r.type() == OabRegistrationType.PRINCIPAL).count();
		return principal <= 1 || violation(context, "só pode haver uma inscrição principal");
	}

	/** Inscrições incompletas já são apontadas pela validação de cada item. */
	private static List<OabRegistrationRequest> complete(List<OabRegistrationRequest> registrations) {
		if (registrations == null) {
			return List.of();
		}
		return registrations.stream()
			.filter(Objects::nonNull)
			.filter(r -> r.number() != null && r.state() != null && r.type() != null)
			.toList();
	}

	private static boolean hasRepeated(List<OabRegistrationRequest> registrations) {
		long distinct = registrations.stream()
			.map(r -> List.of(OabRegistration.normalizeNumber(r.number()), r.state(), r.type()))
			.distinct()
			.count();
		return distinct != registrations.size();
	}

	private static boolean violation(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(FIELD).addConstraintViolation();
		return false;
	}

}
