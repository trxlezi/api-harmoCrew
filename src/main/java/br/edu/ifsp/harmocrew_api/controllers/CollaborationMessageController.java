package br.edu.ifsp.harmocrew_api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsp.harmocrew_api.services.AccessGuard;
import br.edu.ifsp.harmocrew_api.dtos.CollaborationMessageRequest;
import br.edu.ifsp.harmocrew_api.dtos.CollaborationMessageResponse;
import br.edu.ifsp.harmocrew_api.services.CollaborationMessageService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CollaborationMessageController {

	/*
	 * Controller REST das mensagens.
	 *
	 * Ele define as URLs e codigos HTTP. A regra de negocio fica no service.
	 * Assim fica facil explicar a separacao em camadas:
	 * Controller = HTTP, Service = regra, Repository = banco.
	 */
	private final CollaborationMessageService messageService;
	private final AccessGuard guard;

	public CollaborationMessageController(CollaborationMessageService messageService, AccessGuard guard) {
		this.guard = guard;
		this.messageService = messageService;
	}

	@GetMapping("/messages")
	public List<CollaborationMessageResponse> list(@RequestParam(required = false) Long projectId) {
		// Exemplo: GET /api/messages?projectId=1 filtra por projeto.
		return guard.visible(projectId, messageService.list(projectId), r -> r.projectId());
	}

	@GetMapping("/projects/{projectId}/messages")
	public List<CollaborationMessageResponse> listByProject(@PathVariable Long projectId) {
		guard.requireMember(projectId);
		// Rota alternativa mais RESTful para listar mensagens de um projeto.
		return messageService.list(projectId);
	}

	@PostMapping("/projects/{projectId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public CollaborationMessageResponse create(
		@PathVariable Long projectId,
		@Valid @RequestBody CollaborationMessageRequest request
	) {
		guard.requireMember(projectId);
		// Retorna 201 Created porque cria um novo registro no banco.
		return messageService.create(projectId, request);
	}

	@DeleteMapping("/messages/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		guard.requireMessage(id);
		messageService.delete(id);
	}
}
