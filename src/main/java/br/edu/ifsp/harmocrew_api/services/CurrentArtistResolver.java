package br.edu.ifsp.harmocrew_api.services;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.edu.ifsp.harmocrew_api.entities.Artist;
import br.edu.ifsp.harmocrew_api.entities.User;
import br.edu.ifsp.harmocrew_api.enums.UserRole;
import br.edu.ifsp.harmocrew_api.exceptions.ForbiddenException;
import br.edu.ifsp.harmocrew_api.repositories.UserRepository;

/*
 * Descobre qual artista esta executando a acao (autor do upload, votante...).
 *
 * Regra de seguranca: o artista e sempre o do usuario do token JWT. Um
 * artistId enviado no corpo/parametro so e aceito se for o proprio artista do
 * usuario; caso contrario a requisicao e recusada com 403 (nao da para votar,
 * enviar arquivo ou ler notificacoes como outra pessoa). Apenas ADMIN pode
 * agir em nome de outro artista.
 */
@Component
public class CurrentArtistResolver {

	private final ArtistService artistService;
	private final UserRepository userRepository;

	public CurrentArtistResolver(ArtistService artistService, UserRepository userRepository) {
		this.artistService = artistService;
		this.userRepository = userRepository;
	}

	public Artist resolve(Long explicitArtistId) {
		Optional<User> user = currentUser();
		Artist own = user.map(User::getArtist).orElse(null);
		if (explicitArtistId == null) {
			return own;
		}
		if (own != null && own.getId().equals(explicitArtistId)) {
			return own;
		}
		if (isAdmin(user)) {
			return artistService.findEntity(explicitArtistId);
		}
		throw new ForbiddenException("Você só pode agir como o seu próprio perfil de artista");
	}

	public Optional<User> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getName() == null) {
			return Optional.empty();
		}
		return userRepository.findByEmail(authentication.getName());
	}

	public boolean isAdmin() {
		return isAdmin(currentUser());
	}

	private static boolean isAdmin(Optional<User> user) {
		return user.map(u -> u.getRole() == UserRole.ADMIN).orElse(false);
	}
}
