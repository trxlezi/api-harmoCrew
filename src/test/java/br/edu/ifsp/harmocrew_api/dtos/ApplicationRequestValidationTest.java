package br.edu.ifsp.harmocrew_api.dtos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/*
 * Testes de unidade da validacao de ApplicationRequest (RN04), com analise de
 * valor limite.
 *
 * RN04: mensagem (1 a 2000 caracteres), especialidade (1 a 120) e
 * disponibilidade (1 a 160) sao obrigatorias; artistId e obrigatorio.
 *
 * DRIVER: cada metodo @Test monta uma requisicao e chama validator.validate,
 * no lugar do controller (@Valid), e confere as violacoes.
 * Nao ha mocks nem stubs: o Validator do Jakarta (Hibernate Validator) e o
 * real, e o record nao tem dependencias.
 */
class ApplicationRequestValidationTest {

	private static final Long ARTIST_ID = 1L;
	private static final String MESSAGE = "Toco baixo ha 10 anos";
	private static final String SPECIALTY = "Baixo";
	private static final String AVAILABILITY = "Noites";

	private Validator validator;

	@BeforeEach
	void setUp() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@Test
	@DisplayName("RN04: requisicao valida nao tem violacoes")
	void validRequestHasNoViolations() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, SPECIALTY, AVAILABILITY))).isEmpty();
	}

	// Campos em branco (vazio e so espacos).

	@Test
	@DisplayName("RN04: mensagem vazia e invalida")
	void emptyMessageIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, "", SPECIALTY, AVAILABILITY)))
			.containsExactly("message");
	}

	@Test
	@DisplayName("RN04: mensagem so com espacos e invalida")
	void blankMessageIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, "   ", SPECIALTY, AVAILABILITY)))
			.containsExactly("message");
	}

	@Test
	@DisplayName("RN04: especialidade vazia e invalida")
	void emptySpecialtyIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, "", AVAILABILITY)))
			.containsExactly("specialty");
	}

	@Test
	@DisplayName("RN04: especialidade so com espacos e invalida")
	void blankSpecialtyIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, "   ", AVAILABILITY)))
			.containsExactly("specialty");
	}

	@Test
	@DisplayName("RN04: disponibilidade vazia e invalida")
	void emptyAvailabilityIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, SPECIALTY, "")))
			.containsExactly("availability");
	}

	@Test
	@DisplayName("RN04: disponibilidade so com espacos e invalida")
	void blankAvailabilityIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, SPECIALTY, "   ")))
			.containsExactly("availability");
	}

	// Valor limite: exatamente no maximo (valido).

	@Test
	@DisplayName("RN04: mensagem com exatamente 2000 caracteres e valida")
	void messageAtLimitIsValid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, "a".repeat(2000), SPECIALTY, AVAILABILITY)))
			.isEmpty();
	}

	@Test
	@DisplayName("RN04: especialidade com exatamente 120 caracteres e valida")
	void specialtyAtLimitIsValid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, "a".repeat(120), AVAILABILITY)))
			.isEmpty();
	}

	@Test
	@DisplayName("RN04: disponibilidade com exatamente 160 caracteres e valida")
	void availabilityAtLimitIsValid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, SPECIALTY, "a".repeat(160))))
			.isEmpty();
	}

	// Valor limite: um caractere acima do maximo (invalido).

	@Test
	@DisplayName("RN04: mensagem com 2001 caracteres e invalida")
	void messageAboveLimitIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, "a".repeat(2001), SPECIALTY, AVAILABILITY)))
			.containsExactly("message");
	}

	@Test
	@DisplayName("RN04: especialidade com 121 caracteres e invalida")
	void specialtyAboveLimitIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, "a".repeat(121), AVAILABILITY)))
			.containsExactly("specialty");
	}

	@Test
	@DisplayName("RN04: disponibilidade com 161 caracteres e invalida")
	void availabilityAboveLimitIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(ARTIST_ID, MESSAGE, SPECIALTY, "a".repeat(161))))
			.containsExactly("availability");
	}

	@Test
	@DisplayName("RN04: artistId nulo e invalido")
	void nullArtistIdIsInvalid() {
		assertThat(violatedFields(new ApplicationRequest(null, MESSAGE, SPECIALTY, AVAILABILITY)))
			.containsExactly("artistId");
	}

	private Set<String> violatedFields(ApplicationRequest request) {
		Set<ConstraintViolation<ApplicationRequest>> violations = validator.validate(request);
		return violations.stream()
			.map(violation -> violation.getPropertyPath().toString())
			.collect(Collectors.toSet());
	}
}
