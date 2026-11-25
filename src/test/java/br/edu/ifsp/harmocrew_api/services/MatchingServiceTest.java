package br.edu.ifsp.harmocrew_api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.edu.ifsp.harmocrew_api.dtos.MatchResponse.CriterionScore;
import br.edu.ifsp.harmocrew_api.entities.Artist;
import br.edu.ifsp.harmocrew_api.entities.MusicalProject;
import br.edu.ifsp.harmocrew_api.repositories.ApplicationRepository;
import br.edu.ifsp.harmocrew_api.repositories.ArtistRepository;

/*
 * Testes de unidade de MatchingService.score (RN05).
 *
 * DRIVER: cada metodo @Test chama score(artista, projeto) e confere a nota.
 * MOCK: o construtor exige ArtistRepository, ApplicationRepository e
 * MusicalProjectService; score() nao usa nenhum deles, entao sao mocks sem
 * comportamento (nenhum stub e necessario).
 */
class MatchingServiceTest {

	private MatchingService service;

	@BeforeEach
	void setUp() {
		// MOCKS: dependencias que score() nao usa.
		service = new MatchingService(mock(ArtistRepository.class), mock(ApplicationRepository.class),
			mock(MusicalProjectService.class));
	}

	@Test
	@DisplayName("RN05: perfil que atende todos os criterios tem nota 100 (sem acento/maiusculas)")
	void allCriteriaMatchScores100() {
		MusicalProject project = project("Rock", Set.of("Baixo"), Set.of("Beatles"), "Bragança Paulista");
		Artist artist = artist(Set.of("rock"), Set.of("baixo"), "Vocal", Set.of("beatles"), "Braganca Paulista");

		assertThat(service.score(artist, project).score()).isEqualTo(100);
	}

	@Test
	@DisplayName("RN05: perfil que nao atende nenhum criterio tem nota 0")
	void noCriteriaMatchScores0() {
		MusicalProject project = project("Jazz", Set.of("Baixo"), Set.of("Beatles"), "Campinas");
		Artist artist = artist(Set.of("Rock"), Set.of("Guitarra"), "Guitarra", Set.of("Metallica"), "Atibaia");

		assertThat(service.score(artist, project).score()).isZero();
	}

	@Test
	@DisplayName("RN05: vaga sem influencias renormaliza os pesos -> (35 + 0 + 15) / 80 = 62,5 -> 63")
	void missingCriterionRenormalizesWeights() {
		MusicalProject project = project("Rock", Set.of("Bateria"), Set.of(), "Campinas");
		Artist artist = artist(Set.of("Rock"), Set.of("Guitarra"), "Guitarra", Set.of("Beatles"), "Campinas");

		MatchingService.Score score = service.score(artist, project);

		assertThat(score.score()).isEqualTo(63);
		assertThat(score.breakdown())
			.filteredOn(criterion -> criterion.criterion().equals("INFLUENCES"))
			.extracting(CriterionScore::applied)
			.containsExactly(false);
	}

	@Test
	@DisplayName("RN05: a especialidade principal do perfil (mainSpecialty) conta como instrumento")
	void mainSpecialtyCountsAsInstrument() {
		MusicalProject project = project(null, Set.of("Baixo"), Set.of(), null);
		Artist artist = artist(Set.of(), Set.of(), "Baixo", Set.of(), null);

		MatchingService.Score score = service.score(artist, project);

		assertThat(score.score()).isEqualTo(100);
		assertThat(score.matchedInstruments()).containsExactly("Baixo");
	}

	@Test
	@DisplayName("RN05: perfil sem especialidade principal (null) usa so os instrumentos -> 1 de 2 = 50")
	void nullMainSpecialtyUsesOnlyInstruments() {
		MusicalProject project = project(null, Set.of("Baixo", "Guitarra"), Set.of(), null);
		Artist artist = artist(Set.of(), Set.of("Guitarra"), null, Set.of(), null);

		MatchingService.Score score = service.score(artist, project);

		assertThat(score.score()).isEqualTo(50);
		assertThat(score.matchedInstruments()).containsExactly("Guitarra");
	}

	@Test
	@DisplayName("RN05: vaga sem instrumentos renormaliza os pesos -> (35 + 0 + 15) / 70 = 71,4 -> 71")
	void missingNeedsRenormalizesWeights() {
		MusicalProject project = project("Rock", Set.of(), Set.of("Beatles"), "Campinas");
		Artist artist = artist(Set.of("Rock"), Set.of("Baixo"), "Baixo", Set.of("Metallica"), "Campinas");

		MatchingService.Score score = service.score(artist, project);

		assertThat(score.score()).isEqualTo(71);
		assertThat(score.matchedInstruments()).isEmpty();
		assertThat(score.breakdown())
			.filteredOn(criterion -> criterion.criterion().equals("INSTRUMENTS"))
			.extracting(CriterionScore::applied)
			.containsExactly(false);
	}

	@Test
	@DisplayName("RN05: vaga sem nenhum criterio tem peso aplicado 0 e nota 0")
	void noCriteriaAppliedScores0() {
		MusicalProject project = project(null, Set.of(), Set.of(), null);
		Artist artist = artist(Set.of("Rock"), Set.of("Baixo"), "Baixo", Set.of("Beatles"), "Campinas");

		MatchingService.Score score = service.score(artist, project);

		assertThat(score.score()).isZero();
		assertThat(score.breakdown()).extracting(CriterionScore::applied).containsOnly(false);
		assertThat(score.breakdown().stream().filter(CriterionScore::applied).mapToInt(CriterionScore::weight).sum())
			.isZero();
	}

	private static MusicalProject project(String style, Set<String> needs, Set<String> influences, String location) {
		MusicalProject project = new MusicalProject();
		project.setMusicalStyle(style);
		project.getNeeds().addAll(needs);
		project.getDesiredInfluences().addAll(influences);
		project.setLocation(location);
		return project;
	}

	private static Artist artist(Set<String> styles, Set<String> instruments, String mainSpecialty,
		Set<String> influences, String city) {
		Artist artist = new Artist();
		artist.setStageName("Teste");
		artist.getMusicalStyles().addAll(styles);
		artist.getInstruments().addAll(instruments);
		artist.setMainSpecialty(mainSpecialty);
		artist.getInfluences().addAll(influences);
		artist.setCity(city);
		return artist;
	}
}
