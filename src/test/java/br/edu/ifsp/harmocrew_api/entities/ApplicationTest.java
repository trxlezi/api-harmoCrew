package br.edu.ifsp.harmocrew_api.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.edu.ifsp.harmocrew_api.enums.ApplicationStatus;

/*
 * Testes de unidade da entidade Application.
 *
 * DRIVER: cada metodo @Test cria a entidade e chama seus metodos.
 * Nao ha mocks nem stubs: a entidade nao tem dependencias.
 * createdAt e preenchido pelo callback JPA @PrePersist, por isso o teste
 * chama prePersist() diretamente, sem banco de dados.
 */
class ApplicationTest {

	@Test
	@DisplayName("Nova candidatura comeca com status Pendente")
	void initialStatusIsPending() {
		assertThat(new Application().getStatus()).isEqualTo(ApplicationStatus.PENDING);
	}

	@Test
	@DisplayName("prePersist preenche a data de criacao quando ela esta vazia")
	void prePersistFillsCreatedAt() {
		Application application = new Application();
		assertThat(application.getCreatedAt()).isNull();

		LocalDateTime before = LocalDateTime.now();
		application.prePersist();

		assertThat(application.getCreatedAt()).isNotNull().isBetween(before, LocalDateTime.now());
	}

	@Test
	@DisplayName("prePersist preserva uma data de criacao ja definida")
	void prePersistKeepsExistingCreatedAt() {
		Application application = new Application();
		LocalDateTime original = LocalDateTime.of(2026, 1, 15, 10, 30);
		application.setCreatedAt(original);

		application.prePersist();

		assertThat(application.getCreatedAt()).isEqualTo(original);
	}
}
