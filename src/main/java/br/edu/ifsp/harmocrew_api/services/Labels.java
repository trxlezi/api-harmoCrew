package br.edu.ifsp.harmocrew_api.services;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import br.edu.ifsp.harmocrew_api.enums.ApplicationStatus;
import br.edu.ifsp.harmocrew_api.enums.TaskStatus;

// Textos em portugues para notificacoes e log de auditoria (nunca o nome do enum).
public final class Labels {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private Labels() {
	}

	public static String task(TaskStatus status) {
		if (status == null) {
			return "—";
		}
		return switch (status) {
			case TODO -> "A fazer";
			case IN_PROGRESS -> "Em andamento";
			case DONE -> "Concluída";
		};
	}

	public static String application(ApplicationStatus status) {
		if (status == null) {
			return "—";
		}
		return switch (status) {
			case PENDING -> "Recebida";
			case UNDER_REVIEW -> "Em análise";
			case SHORTLISTED -> "Pré-selecionada";
			case APPROVED -> "Aprovada";
			case REJECTED -> "Não selecionada";
		};
	}

	public static String date(LocalDate date) {
		return date == null ? "" : date.format(DATE);
	}
}
