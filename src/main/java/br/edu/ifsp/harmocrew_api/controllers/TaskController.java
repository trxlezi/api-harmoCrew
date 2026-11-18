package br.edu.ifsp.harmocrew_api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsp.harmocrew_api.services.AccessGuard;
import br.edu.ifsp.harmocrew_api.dtos.TaskRequest;
import br.edu.ifsp.harmocrew_api.dtos.TaskResponse;
import br.edu.ifsp.harmocrew_api.dtos.TaskStatusUpdateRequest;
import br.edu.ifsp.harmocrew_api.services.TaskService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TaskController {

	private final TaskService taskService;
	private final AccessGuard guard;

	public TaskController(TaskService taskService, AccessGuard guard) {
		this.guard = guard;
		this.taskService = taskService;
	}

	@GetMapping("/tasks")
	public List<TaskResponse> list(@RequestParam(required = false) Long projectId) {
		return guard.visible(projectId, taskService.list(projectId), r -> r.projectId());
	}

	@GetMapping("/tasks/{id}")
	public TaskResponse get(@PathVariable Long id) {
		guard.requireTask(id);
		return taskService.get(id);
	}

	@GetMapping("/projects/{projectId}/tasks")
	public List<TaskResponse> listByProject(@PathVariable Long projectId) {
		guard.requireMember(projectId);
		return taskService.list(projectId);
	}

	@PostMapping("/projects/{projectId}/tasks")
	@ResponseStatus(HttpStatus.CREATED)
	public TaskResponse create(@PathVariable Long projectId, @Valid @RequestBody TaskRequest request) {
		guard.requireMember(projectId);
		return taskService.create(projectId, request);
	}

	@PutMapping("/tasks/{id}")
	public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
		guard.requireTask(id);
		return taskService.update(id, request);
	}

	@PatchMapping("/tasks/{id}/status")
	public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
		guard.requireTask(id);
		return taskService.updateStatus(id, request);
	}

	@DeleteMapping("/tasks/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		guard.requireTask(id);
		taskService.delete(id);
	}
}
