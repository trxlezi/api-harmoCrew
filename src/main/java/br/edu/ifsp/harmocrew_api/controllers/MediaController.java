package br.edu.ifsp.harmocrew_api.controllers;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsp.harmocrew_api.exceptions.ForbiddenException;
import br.edu.ifsp.harmocrew_api.security.MediaLinks;
import br.edu.ifsp.harmocrew_api.services.StorageService;

/*
 * Download de arquivos por link imutavel /media/{chave}.
 * A chave (UUID) nunca e sobrescrita: cada versao antiga continua no mesmo
 * endereco para sempre. O acesso exige assinatura valida (ver MediaLinks),
 * emitida apenas para quem pode ver o arquivo.
 */
@RestController
public class MediaController {

	private final StorageService storageService;
	private final MediaLinks mediaLinks;

	public MediaController(StorageService storageService, MediaLinks mediaLinks) {
		this.storageService = storageService;
		this.mediaLinks = mediaLinks;
	}

	@GetMapping("/media/{key}")
	public ResponseEntity<Resource> get(
		@PathVariable String key,
		@RequestParam(required = false) Long exp,
		@RequestParam(required = false) String sig
	) {
		if (!mediaLinks.verify(key, exp, sig)) {
			throw new ForbiddenException("Link de mídia inválido ou expirado");
		}
		Resource resource = storageService.load(key);
		// Conteudo imutavel, mas o cache e privado: so o dono do link guarda copia.
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePrivate().immutable())
			.contentType(MediaType.parseMediaType(storageService.probeContentType(key)))
			.body(resource);
	}
}
