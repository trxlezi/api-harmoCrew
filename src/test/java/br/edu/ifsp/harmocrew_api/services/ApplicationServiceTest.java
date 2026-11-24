package br.edu.ifsp.harmocrew_api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.edu.ifsp.harmocrew_api.dtos.ApplicationRequest;
import br.edu.ifsp.harmocrew_api.dtos.ApplicationResponse;
import br.edu.ifsp.harmocrew_api.dtos.ApplicationStatusUpdateRequest;
import br.edu.ifsp.harmocrew_api.entities.Application;
import br.edu.ifsp.harmocrew_api.entities.Artist;
import br.edu.ifsp.harmocrew_api.entities.MusicalProject;
import br.edu.ifsp.harmocrew_api.enums.ApplicationStatus;
import br.edu.ifsp.harmocrew_api.enums.NotificationType;
import br.edu.ifsp.harmocrew_api.exceptions.BusinessException;
import br.edu.ifsp.harmocrew_api.exceptions.ResourceNotFoundException;
import br.edu.ifsp.harmocrew_api.repositories.ApplicationRepository;

/*
 * Testes de unidade de ApplicationService (UC01 - Formar a equipe do projeto musical).
 *
 * DRIVER: cada metodo @Test e um driver, que chama create/updateStatus no
 * lugar do controller e confere o resultado.
 * MOCK: todas as dependencias do service sao mocks do Mockito (@Mock), entao
 * nenhum banco, contexto Spring ou rede e usado.
 * STUB: cada when(...).thenReturn(...) / thenAnswer(...) e um stub, isto e,
 * uma resposta pronta que o mock devolve para o cenario do teste.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

	private static final Long PROJECT_ID = 10L;
	private static final Long ARTIST_ID = 1L;
	private static final Long APPLICATION_ID = 100L;

	// MOCKS: dependencias injetadas pelo construtor de ApplicationService.
	@Mock
	private ApplicationRepository applicationRepository;
	@Mock
	private MusicalProjectService projectService;
	@Mock
	private ArtistService artistService;
	@Mock
	private MatchingService matchingService;
	@Mock
	private NotificationService notificationService;
	@Mock
	private AuditLogService auditLogService;
	// MOCK: CurrentArtistResolver e injetado por campo (@Autowired) no service,
	// por isso e colocado com ReflectionTestUtils no setUp.
	@Mock
	private CurrentArtistResolver currentArtist;

	private ApplicationService service;
	private MusicalProject project;
	private Artist artist;

	@BeforeEach
	void setUp() {
		service = new ApplicationService(applicationRepository, projectService, artistService, matchingService,
			notificationService, auditLogService);
		ReflectionTestUtils.setField(service, "currentArtist", currentArtist);

		project = new MusicalProject();
		project.setId(PROJECT_ID);
		project.setTitle("Banda de Rock");
		project.setMusicalStyle("Rock");

		artist = artist(ARTIST_ID, "Ana Baixo");
	}

	@Test
	@DisplayName("Fluxo principal: candidatura valida e criada como Pendente, com nota e campos sem espacos")
	void createValidApplication() {
		stubValidCreate(List.of());

		ApplicationResponse response = service.create(PROJECT_ID, request("  Toco baixo ha 10 anos  "));

		assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
		assertThat(response.matchScore()).isEqualTo(80);
		assertThat(response.message()).isEqualTo("Toco baixo ha 10 anos");
		assertThat(response.specialty()).isEqualTo("Baixo");
		assertThat(response.availability()).isEqualTo("Noites");
		assertThat(response.artistId()).isEqualTo(ARTIST_ID);
		assertThat(response.projectId()).isEqualTo(PROJECT_ID);
		verify(applicationRepository).save(any(Application.class));
		verify(auditLogService).record(eq(PROJECT_ID), eq("APPLICATION_CREATED"), eq("Application"),
			eq(APPLICATION_ID), eq(artist), anyString());
	}

	@Test
	@DisplayName("RN01: usuario sem perfil de artista nao pode se candidatar")
	void createWithoutArtistProfile() {
		when(projectService.findEntity(PROJECT_ID)).thenReturn(project); // STUB
		when(currentArtist.resolve(ARTIST_ID)).thenReturn(null);         // STUB: usuario sem Artist

		assertThatThrownBy(() -> service.create(PROJECT_ID, request("Oi")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Usuário sem perfil de artista");
		verify(applicationRepository, never()).save(any());
	}

	@Test
	@DisplayName("RN02: artista que ja e membro do projeto nao pode se candidatar")
	void createWhenAlreadyMember() {
		project.getArtists().add(artist);
		when(projectService.findEntity(PROJECT_ID)).thenReturn(project); // STUB
		when(currentArtist.resolve(ARTIST_ID)).thenReturn(artist);       // STUB

		assertThatThrownBy(() -> service.create(PROJECT_ID, request("Oi")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Você já faz parte deste projeto");
		verify(applicationRepository, never()).save(any());
	}

	@Test
	@DisplayName("RN03: candidatura duplicada (anterior Pendente) e recusada")
	void createDuplicateApplication() {
		when(projectService.findEntity(PROJECT_ID)).thenReturn(project);         // STUB
		when(currentArtist.resolve(ARTIST_ID)).thenReturn(artist);               // STUB
		when(applicationRepository.findByProjectId(PROJECT_ID))
			.thenReturn(List.of(existing(artist, ApplicationStatus.PENDING)));   // STUB

		assertThatThrownBy(() -> service.create(PROJECT_ID, request("De novo")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Você já se candidatou a esta vaga");
		verify(applicationRepository, never()).save(any());
	}

	@Test
	@DisplayName("RN03: nova candidatura e aceita depois de a anterior ser Recusada")
	void createAfterRejected() {
		stubValidCreate(List.of(existing(artist, ApplicationStatus.REJECTED)));

		ApplicationResponse response = service.create(PROJECT_ID, request("Tentando de novo"));

		assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
		verify(applicationRepository).save(any(Application.class));
	}

	@Test
	@DisplayName("RN03: candidatura aberta de OUTRO artista nao bloqueia")
	void createWhenOtherArtistApplied() {
		stubValidCreate(List.of(existing(artist(2L, "Bruno Bateria"), ApplicationStatus.PENDING)));

		ApplicationResponse response = service.create(PROJECT_ID, request("Toco baixo"));

		assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
		verify(applicationRepository).save(any(Application.class));
	}

	@Test
	@DisplayName("RN06: aprovar adiciona o artista a equipe e notifica o candidato com o comentario")
	void approveAddsArtistToTeam() {
		Application application = stubFindById(ApplicationStatus.PENDING);

		ApplicationResponse response = service.updateStatus(APPLICATION_ID,
			new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, "  Bem-vindo(a) à banda!  "));

		assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
		assertThat(application.getEvaluationNote()).isEqualTo("Bem-vindo(a) à banda!");
		assertThat(project.getArtists()).contains(artist);
		verify(notificationService).notify(eq(ARTIST_ID), eq(PROJECT_ID),
			eq(NotificationType.APPLICATION_STATUS_CHANGED), anyString(),
			eq("Sua candidatura para Banda de Rock agora está: Aprovada. Comentário: Bem-vindo(a) à banda!"),
			eq(APPLICATION_ID));
		verify(auditLogService).record(eq(PROJECT_ID), eq("APPLICATION_STATUS_CHANGED"), eq("Application"),
			eq(APPLICATION_ID), isNull(), anyString());
	}

	@Test
	@DisplayName("RN06: colocar Em analise notifica, mas nao adiciona a equipe")
	void underReviewNotifiesWithoutJoining() {
		stubFindById(ApplicationStatus.PENDING);

		ApplicationResponse response = service.updateStatus(APPLICATION_ID,
			new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW, null));

		assertThat(response.status()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
		assertThat(response.evaluationNote()).isNull();
		assertThat(project.getArtists()).doesNotContain(artist);
		verify(notificationService).notify(eq(ARTIST_ID), eq(PROJECT_ID),
			eq(NotificationType.APPLICATION_STATUS_CHANGED), anyString(),
			eq("Sua candidatura para Banda de Rock agora está: Em análise"), eq(APPLICATION_ID));
	}

	@Test
	@DisplayName("Mesmo status: nao notifica nem registra auditoria")
	void sameStatusDoesNotNotify() {
		stubFindById(ApplicationStatus.UNDER_REVIEW);

		service.updateStatus(APPLICATION_ID, new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW, null));

		verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
		verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("Comentario em branco apaga o comentario anterior (fica null)")
	void blankNoteClearsEvaluationNote() {
		Application application = stubFindById(ApplicationStatus.PENDING);
		application.setEvaluationNote("comentario antigo");

		ApplicationResponse response = service.updateStatus(APPLICATION_ID,
			new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED, "   "));

		assertThat(response.evaluationNote()).isNull();
		assertThat(application.getEvaluationNote()).isNull();
	}

	@Test
	@DisplayName("Candidatura inexistente: ResourceNotFoundException")
	void updateStatusNotFound() {
		when(applicationRepository.findById(999L)).thenReturn(Optional.empty()); // STUB

		assertThatThrownBy(() -> service.updateStatus(999L,
			new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED, null)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Candidatura não encontrada: 999");
	}

	/*
	 * RN07: Aprovada e Recusada sao estados finais. ESPERA-SE QUE ESTE TESTE
	 * FALHE com o codigo atual: updateStatus nao valida a transicao, aceita
	 * APPROVED -> REJECTED e o artista continua na equipe (defeito).
	 */
	@Test
	@DisplayName("RN07: candidatura Aprovada nao pode ser alterada (deve falhar: defeito)")
	void approvedIsFinal() {
		Application application = stubFindById(ApplicationStatus.APPROVED);
		project.getArtists().add(artist);

		assertThatThrownBy(() -> service.updateStatus(APPLICATION_ID,
			new ApplicationStatusUpdateRequest(ApplicationStatus.REJECTED, "Mudei de ideia")))
			.isInstanceOf(BusinessException.class);
		assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
		assertThat(project.getArtists()).contains(artist);
	}

	// STUBS do caminho feliz de create().
	private void stubValidCreate(List<Application> existingApplications) {
		when(projectService.findEntity(PROJECT_ID)).thenReturn(project);
		when(currentArtist.resolve(ARTIST_ID)).thenReturn(artist);
		when(applicationRepository.findByProjectId(PROJECT_ID)).thenReturn(existingApplications);
		when(matchingService.score(artist, project))
			.thenReturn(new MatchingService.Score(80, List.of(), Set.of(), Set.of()));
		// STUB: save devolve a propria entidade, com id, como o JPA faria.
		when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> {
			Application saved = invocation.getArgument(0);
			saved.setId(APPLICATION_ID);
			return saved;
		});
	}

	// STUB de findById: devolve uma candidatura do artista no projeto.
	private Application stubFindById(ApplicationStatus status) {
		Application application = existing(artist, status);
		application.setId(APPLICATION_ID);
		application.setProject(project);
		when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
		return application;
	}

	private static ApplicationRequest request(String message) {
		return new ApplicationRequest(ARTIST_ID, message, "  Baixo ", " Noites ");
	}

	private Application existing(Artist owner, ApplicationStatus status) {
		Application application = new Application();
		application.setArtist(owner);
		application.setProject(project);
		application.setStatus(status);
		application.setMessage("msg");
		application.setSpecialty("Baixo");
		application.setAvailability("Noites");
		return application;
	}

	private static Artist artist(Long id, String stageName) {
		Artist artist = new Artist();
		artist.setId(id);
		artist.setStageName(stageName);
		artist.setMainSpecialty("Baixo");
		return artist;
	}
}
