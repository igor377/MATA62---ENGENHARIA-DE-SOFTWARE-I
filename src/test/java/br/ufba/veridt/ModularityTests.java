package br.ufba.veridt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(VeridtApplication.class);

	@Test
	void respeitaAsFronteirasDosModulos() {
		modules.verify();
	}

	@Test
	void listaOsSeisModulos() {
		assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
			.containsExactlyInAnyOrder("identity", "credits", "capture", "records", "notification", "storage");
	}

}
