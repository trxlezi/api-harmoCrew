package br.edu.ifsp.harmocrew_api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * Controle de acesso com usuarios reais (sem ADMIN):
 * - dados internos do projeto so para membros;
 * - ninguem age como outro artista;
 * - links de midia exigem assinatura, e versoes antigas seguem acessiveis;
 * - ranking pode ser restrito a quem se candidatou a vaga.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	record Session(String token, Long artistId) {
	}

	@Test
	void shouldRestrictProjectDataToMembersAndSignMediaLinks() throws Exception {
		Session owner = register("Lider");
		Session candidate = register("Candidata");
		Session outsider = register("Curioso");

		Long projectId = create(owner, post("/api/projects"), """
			{"title":"Banda Segura","description":"Teste","musicalStyle":"MPB","status":"ACTIVE","startDate":"2026-01-01",
			 "location":"Sao Paulo","needs":["Baixo"]}
			""");

		// Criador vira membro automaticamente; a vaga em si e publica.
		as(outsider, get("/api/projects/{id}", projectId)).andExpect(status().isOk());
		as(owner, get("/api/projects/{id}", projectId))
			.andExpect(jsonPath("$.artists[0].id").value(owner.artistId().intValue()));

		// Arquivo com duas versoes, enviado pelo lider.
		MvcResult created = as(owner, multipart("/api/projects/{id}/files", projectId)
				.file(new MockMultipartFile("file", "guia.mp3", "audio/mpeg", "guia v1".getBytes()))
				.param("name", "Guia"))
			.andExpect(status().isCreated()).andReturn();
		Long fileId = json(created).get("id").asLong();
		MvcResult second = as(owner, multipart("/api/files/{id}/versions", fileId)
				.file(new MockMultipartFile("file", "guia.mp3", "audio/mpeg", "guia v2".getBytes())))
			.andExpect(status().isCreated()).andReturn();
		String v1Url = json(second).get("versions").get(1).get("url").asText();
		assertThat(v1Url).contains("?exp=").contains("&sig=");

		// Nao-membro nao ve arquivos, versoes, historico, ranking nem dashboard.
		as(outsider, get("/api/projects/{id}/files", projectId)).andExpect(status().isForbidden());
		as(outsider, get("/api/files/{id}/versions/1", fileId)).andExpect(status().isForbidden());
		as(outsider, get("/api/files/{id}/history", fileId)).andExpect(status().isForbidden());
		as(outsider, get("/api/projects/{id}/matches", projectId)).andExpect(status().isForbidden());
		as(outsider, get("/api/projects/{id}/dashboard", projectId)).andExpect(status().isForbidden());
		as(outsider, get("/api/projects/{id}/tasks", projectId)).andExpect(status().isForbidden());

		// Link antigo (v1) continua valido e imutavel; sem assinatura ou adulterado = 403.
		mockMvc.perform(get(v1Url)).andExpect(status().isOk()).andExpect(content().bytes("guia v1".getBytes()));
		String bareUrl = v1Url.substring(0, v1Url.indexOf('?'));
		mockMvc.perform(get(bareUrl)).andExpect(status().isForbidden());
		mockMvc.perform(get(v1Url.replaceAll("sig=.", "sig=X"))).andExpect(status().isForbidden());

		// Ninguem se candidata/vota/le notificacoes como outro artista.
		as(outsider, post("/api/projects/{id}/applications", projectId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"artistId\":%d,\"message\":\"fake\",\"specialty\":\"Baixo\",\"availability\":\"Noites\"}".formatted(candidate.artistId())))
			.andExpect(status().isForbidden());
		as(outsider, get("/api/notifications").param("artistId", owner.artistId().toString()))
			.andExpect(status().isForbidden());

		// Candidata se candidata com o proprio perfil.
		Long applicationId = create(candidate, post("/api/projects/{id}/applications", projectId), """
			{"artistId":%d,"message":"Toco baixo","specialty":"Baixo","availability":"Noites"}
			""".formatted(candidate.artistId()));

		// Nao pode se candidatar duas vezes enquanto a candidatura esta aberta.
		as(candidate, post("/api/projects/{id}/applications", projectId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"artistId\":%d,\"message\":\"de novo\",\"specialty\":\"Baixo\",\"availability\":\"Noites\"}"
				.formatted(candidate.artistId())))
			.andExpect(status().isBadRequest());

		// Ranking restrito a quem se candidatou: so a candidata aparece.
		JsonNode applied = json(as(owner, get("/api/projects/{id}/matches", projectId).param("appliedOnly", "true"))
			.andExpect(status().isOk()).andReturn());
		assertThat(applied).hasSize(1);
		assertThat(applied.get(0).get("artistId").asLong()).isEqualTo(candidate.artistId());
		as(owner, get("/api/projects/{id}/applications/ranked", projectId).param("status", "PENDING"))
			.andExpect(jsonPath("$.length()").value(1));

		// Candidata ve a propria candidatura, mas nao aprova a si mesma.
		as(candidate, get("/api/applications/{id}", applicationId)).andExpect(status().isOk());
		as(candidate, patch("/api/applications/{id}/status", applicationId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"APPROVED\"}")).andExpect(status().isForbidden());

		// Notificacao do lider nao pode ser marcada como lida por outro.
		JsonNode ownerAlerts = json(as(owner, get("/api/notifications")).andExpect(status().isOk()).andReturn());
		if (ownerAlerts.size() > 0) {
			as(outsider, patch("/api/notifications/{id}/read", ownerAlerts.get(0).get("id").asLong()))
				.andExpect(status().isForbidden());
		}

		// Aprovada, vira membro e passa a acessar os arquivos.
		as(owner, patch("/api/applications/{id}/status", applicationId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk());
		as(candidate, get("/api/projects/{id}/files", projectId)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].versions.length()").value(2));

		// Listagem global so devolve projetos do usuario.
		create(owner, post("/api/projects/{id}/tasks", projectId), """
			{"title":"Gravar guia","status":"TODO","priority":"MEDIUM","responsibleName":"Lider","stage":"RECORDING","dueDate":"2026-12-20"}
			""");
		as(outsider, get("/api/tasks")).andExpect(jsonPath("$.length()").value(0));
		as(candidate, get("/api/tasks")).andExpect(jsonPath("$.length()").value(1));
		as(owner, get("/api/projects/{id}/dashboard", projectId))
			.andExpect(jsonPath("$.recentTasks[0].title").value("Gravar guia"));
	}

	private Session register(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"%s","email":"%s","password":"secret123","stageName":"%s","mainSpecialty":"Baixo"}
					""".formatted(name, "s" + UUID.randomUUID() + "@harmocrew.test", name)))
			.andExpect(status().isCreated()).andReturn();
		JsonNode body = json(result);
		return new Session(body.get("token").asText(), body.get("artistId").asLong());
	}

	private org.springframework.test.web.servlet.ResultActions as(Session session, org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> builder)
		throws Exception {
		return mockMvc.perform(builder.header("Authorization", "Bearer " + session.token()));
	}

	private Long create(Session session, MockHttpServletRequestBuilder builder, String body) throws Exception {
		MvcResult result = as(session, builder.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated()).andReturn();
		return json(result).get("id").asLong();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
