package br.ufba.veridt.identity.internal.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** CPF com dígitos verificadores válidos, com ou sem máscara. Nulo é aceito: use com {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = CpfValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Cpf {

	String message() default "CPF inválido";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
