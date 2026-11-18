package br.ufba.veridt.credits.internal.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentSimulationRequest(@NotNull Boolean approved) {
}
