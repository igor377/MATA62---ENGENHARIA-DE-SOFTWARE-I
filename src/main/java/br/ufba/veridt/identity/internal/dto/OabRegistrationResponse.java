package br.ufba.veridt.identity.internal.dto;

import br.ufba.veridt.identity.internal.domain.OabRegistrationType;
import br.ufba.veridt.identity.internal.domain.Uf;

public record OabRegistrationResponse(String number, Uf state, OabRegistrationType type) {
}
