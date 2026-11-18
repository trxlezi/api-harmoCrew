package br.edu.ifsp.harmocrew_api.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.edu.ifsp.harmocrew_api.entities.Application;
import br.edu.ifsp.harmocrew_api.entities.Artist;
import br.edu.ifsp.harmocrew_api.entities.MusicalProject;
import br.edu.ifsp.harmocrew_api.entities.Notification;
import br.edu.ifsp.harmocrew_api.exceptions.ForbiddenException;
import br.edu.ifsp.harmocrew_api.exceptions.ResourceNotFoundException;
import br.edu.ifsp.harmocrew_api.repositories.ApplicationRepository;
import br.edu.ifsp.harmocrew_api.repositories.CollaborationMessageRepository;
import br.edu.ifsp.harmocrew_api.repositories.DecisionRecordRepository;
import br.edu.ifsp.harmocrew_api.repositories.MusicalProjectRepository;
import br.edu.ifsp.harmocrew_api.repositories.NotificationRepository;
import br.edu.ifsp.harmocrew_api.repositories.PollRepository;
import br.edu.ifsp.harmocrew_api.repositories.ProjectFileRepository;
import br.edu.ifsp.harmocrew_api.repositories.RehearsalRepository;
import br.edu.ifsp.harmocrew_api.repositories.TaskRepository;
import br.edu.ifsp.harmocrew_api.repositories.WeeklyGoalRepository;

/*
 * Controle de acesso por projeto.
 *
 * Dados internos de um projeto (arquivos e versoes, enquetes, decisoes,
 * tarefas, ensaios, metas, mensagens, timeline, dashboard, ranking e lista de
 * candidatos) so podem ser lidos/alterados por membros do projeto ou ADMIN.
 * Os dados publicos da vaga (titulo, estilo, necessidades) continuam abertos
 * para qualquer usuario logado, para que musicos possam se candidatar.
 */
@Component
@Transactional(readOnly = true)
public class AccessGuard {

	private final CurrentArtistResolver currentArtist;
	private final MusicalProjectRepository projects;
	private final ProjectFileRepository files;
	private final PollRepository polls;
	private final TaskRepository tasks;
	private final RehearsalRepository rehearsals;
	private final WeeklyGoalRepository goals;
	private final DecisionRecordRepository decisions;
	private final CollaborationMessageRepository messages;
	private final ApplicationRepository applications;
	private final NotificationRepository notifications;

	public AccessGuard(CurrentArtistResolver currentArtist, MusicalProjectRepository projects,
		ProjectFileRepository files, PollRepository polls, TaskRepository tasks, RehearsalRepository rehearsals,
		WeeklyGoalRepository goals, DecisionRecordRepository decisions, CollaborationMessageRepository messages,
		ApplicationRepository applications, NotificationRepository notifications) {
		this.currentArtist = currentArtist;
		this.projects = projects;
		this.files = files;
		this.polls = polls;
		this.tasks = tasks;
		this.rehearsals = rehearsals;
		this.goals = goals;
		this.decisions = decisions;
		this.messages = messages;
		this.applications = applications;
		this.notifications = notifications;
	}

	public void requireMember(Long projectId) {
		MusicalProject project = projects.findById(projectId)
			.orElseThrow(() -> notFound("Projeto", projectId));
		if (currentArtist.isAdmin()) {
			return;
		}
		Artist me = currentArtist.resolve(null);
		boolean member = me != null && project.getArtists().stream().anyMatch(a -> a.getId().equals(me.getId()));
		if (!member) {
			throw new ForbiddenException("Acesso restrito aos membros do projeto");
		}
	}

	public void requireFile(Long fileId) {
		requireMember(files.findById(fileId).orElseThrow(() -> notFound("Arquivo", fileId)).getProject().getId());
	}

	public void requirePoll(Long pollId) {
		requireMember(polls.findById(pollId).orElseThrow(() -> notFound("Enquete", pollId)).getProject().getId());
	}

	public void requireTask(Long taskId) {
		requireMember(tasks.findById(taskId).orElseThrow(() -> notFound("Tarefa", taskId)).getProject().getId());
	}

	public void requireRehearsal(Long id) {
		requireMember(rehearsals.findById(id).orElseThrow(() -> notFound("Ensaio", id)).getProject().getId());
	}

	public void requireGoal(Long id) {
		requireMember(goals.findById(id).orElseThrow(() -> notFound("Meta", id)).getProject().getId());
	}

	public void requireDecision(Long id) {
		requireMember(decisions.findById(id).orElseThrow(() -> notFound("Decisão", id)).getProject().getId());
	}

	public void requireMessage(Long id) {
		requireMember(messages.findById(id).orElseThrow(() -> notFound("Mensagem", id)).getProject().getId());
	}

	// Ver candidatura: o proprio candidato ou membros do projeto.
	public void requireApplicationView(Long id) {
		Application application = applications.findById(id).orElseThrow(() -> notFound("Candidatura", id));
		Artist me = currentArtist.resolve(null);
		if (me != null && application.getArtist() != null && application.getArtist().getId().equals(me.getId())) {
			return;
		}
		requireMember(application.getProject().getId());
	}

	// Aprovar/recusar candidatura: so membros do projeto.
	public void requireApplicationManage(Long id) {
		requireMember(applications.findById(id).orElseThrow(() -> notFound("Candidatura", id)).getProject().getId());
	}

	public void requireOwnNotification(Long id) {
		Notification notification = notifications.findById(id).orElseThrow(() -> notFound("Notificação", id));
		if (currentArtist.isAdmin()) {
			return;
		}
		Artist me = currentArtist.resolve(null);
		if (me == null || !me.getId().equals(notification.getRecipientArtistId())) {
			throw new ForbiddenException("Notificação pertence a outro artista");
		}
	}

	/*
	 * Listagens globais (/api/tasks, /api/rehearsals...): com projectId exige
	 * ser membro; sem projectId devolve so itens dos projetos do usuario.
	 */
	public <T> java.util.List<T> visible(Long projectId, java.util.List<T> items,
		java.util.function.Function<T, Long> projectOf) {
		if (projectId != null) {
			requireMember(projectId);
			return items;
		}
		if (currentArtist.isAdmin()) {
			return items;
		}
		Artist me = currentArtist.resolve(null);
		if (me == null) {
			return java.util.List.of();
		}
		java.util.Set<Long> mine = projects.findAll().stream()
			.filter(p -> p.getArtists().stream().anyMatch(a -> a.getId().equals(me.getId())))
			.map(MusicalProject::getId)
			.collect(java.util.stream.Collectors.toSet());
		return items.stream().filter(item -> mine.contains(projectOf.apply(item))).toList();
	}

	/*
	 * Candidaturas: membros veem as do projeto; o candidato ve as proprias
	 * (?artistId= do proprio usuario), mesmo sem ser membro.
	 */
	public java.util.List<br.edu.ifsp.harmocrew_api.dtos.ApplicationResponse> visible(Long projectId,
		java.util.List<br.edu.ifsp.harmocrew_api.dtos.ApplicationResponse> items,
		java.util.function.Function<br.edu.ifsp.harmocrew_api.dtos.ApplicationResponse, Long> projectOf, Long artistId) {
		if (currentArtist.isAdmin()) {
			return items;
		}
		Artist me = currentArtist.resolve(null);
		if (artistId != null && projectId == null) {
			currentArtist.resolve(artistId);
			return items;
		}
		java.util.List<br.edu.ifsp.harmocrew_api.dtos.ApplicationResponse> own = me == null ? java.util.List.of()
			: items.stream().filter(a -> me.getId().equals(a.artistId())).toList();
		try {
			return visible(projectId, items, projectOf);
		}
		catch (ForbiddenException exception) {
			return own;
		}
	}

	// Dashboard do coletivo: usuario comum ve apenas os proprios projetos.
	public Long dashboardArtist(Long artistId) {
		if (currentArtist.isAdmin()) {
			return artistId;
		}
		Artist me = currentArtist.resolve(artistId);
		if (me == null) {
			throw new ForbiddenException("Usuário sem perfil de artista");
		}
		return me.getId();
	}

	// Editar perfil/enviar audio de um artista: so o dono ou ADMIN.
	public void requireOwnArtist(Long artistId) {
		currentArtist.resolve(artistId);
	}

	private static ResourceNotFoundException notFound(String what, Long id) {
		return new ResourceNotFoundException(what + " não encontrado(a): " + id);
	}
}
