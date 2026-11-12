package br.ufba.veridt.identity.internal.listener;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import br.ufba.veridt.identity.internal.event.PasswordChanged;

/**
 * Encerra as sessões do usuário só depois do commit da nova senha. O Spring Session usa transação própria: apagar antes
 * deixaria uma janela em que um login com a senha antiga criaria uma sessão que sobreviveria à redefinição.
 */
@Component
class PasswordChangedListener {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	PasswordChangedListener(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	@TransactionalEventListener
	void on(PasswordChanged event) {
		sessions.findByPrincipalName(event.userId().toString()).keySet().forEach(sessions::deleteById);
	}

}
