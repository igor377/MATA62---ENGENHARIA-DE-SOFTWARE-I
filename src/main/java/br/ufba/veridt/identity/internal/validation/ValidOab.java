package br.ufba.veridt.identity.internal.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Inscrições na OAB obrigatórias para advogado e ausentes nos demais tipos, sem repetição e com uma só principal. */
@Documented
@Constraint(validatedBy = OabValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOab {

	String message() default "Inscrições OAB inválidas para o tipo de usuário";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
