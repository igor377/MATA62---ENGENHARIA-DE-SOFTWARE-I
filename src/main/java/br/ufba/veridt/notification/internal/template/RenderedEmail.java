package br.ufba.veridt.notification.internal.template;

/** E-mail pronto para envio, com o corpo já em HTML. */
public record RenderedEmail(String subject, String htmlBody) {
}
