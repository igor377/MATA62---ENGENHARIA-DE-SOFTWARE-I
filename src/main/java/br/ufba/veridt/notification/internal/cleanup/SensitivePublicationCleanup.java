package br.ufba.veridt.notification.internal.cleanup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.ufba.veridt.notification.internal.repository.SensitivePublicationRepository;

/**
 * Apaga os eventos já enviados de tipos sensíveis, como a recuperação de senha, cujo link carrega o token em texto
 * puro. O histórico dos demais e-mails é mantido.
 */
@Component
public class SensitivePublicationCleanup {

	private final SensitivePublicationRepository publications;

	public SensitivePublicationCleanup(SensitivePublicationRepository publications) {
		this.publications = publications;
	}

	@Scheduled(fixedDelayString = "${veridt.mail.sensitive-cleanup-interval}")
	public void deleteSentSensitiveEmails() {
		publications.deleteCompleted();
	}

}
