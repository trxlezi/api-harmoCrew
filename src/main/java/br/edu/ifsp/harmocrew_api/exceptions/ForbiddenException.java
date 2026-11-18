package br.edu.ifsp.harmocrew_api.exceptions;

// Usuario autenticado, mas sem permissao para o recurso (HTTP 403).
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
