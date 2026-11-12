package br.ufba.veridt.identity.internal.event;

import java.util.UUID;

/** A senha do usuário foi redefinida. Evento interno do módulo. */
public record PasswordChanged(UUID userId) {
}
