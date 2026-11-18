package br.ufba.veridt.credits.internal.dto;

import java.time.Instant;

public record PixResponse(String qrCodeImage, String copyPaste, Instant expiresAt) {
}
