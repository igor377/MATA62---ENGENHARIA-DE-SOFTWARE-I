package br.ufba.veridt.notification.internal.repository;

import java.util.Arrays;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.ufba.veridt.notification.EmailRequested;
import br.ufba.veridt.notification.EmailType;

/** Apaga direto no banco, sem carregar em memória o histórico de eventos, que só cresce. */
@Repository
public class SensitivePublicationRepository {

	private static final String DELETE_COMPLETED = """
			DELETE FROM event_publication
			WHERE completion_date IS NOT NULL
			  AND event_type = ?
			  AND serialized_event::jsonb ->> 'type' = ANY (?)
			""";

	private final JdbcTemplate jdbc;

	public SensitivePublicationRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void deleteCompleted() {
		String[] sensitiveTypes = Arrays.stream(EmailType.values())
			.filter(EmailType::sensitive)
			.map(EmailType::name)
			.toArray(String[]::new);
		jdbc.update(connection -> {
			var statement = connection.prepareStatement(DELETE_COMPLETED);
			statement.setString(1, EmailRequested.class.getName());
			statement.setArray(2, connection.createArrayOf("text", sensitiveTypes));
			return statement;
		});
	}

}
