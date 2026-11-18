package br.edu.ifsp.harmocrew_api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * Links assinados para /media/{chave}.
 *
 * O caminho /media/{chave} e imutavel (a chave UUID nunca e sobrescrita), mas
 * baixar exige uma assinatura HMAC com validade: ?exp=<epoch>&sig=<hmac>.
 * A API so devolve links assinados para quem tem permissao de ver o recurso
 * (membros do projeto para arquivos; usuarios logados para audio de perfil).
 * Assim o player/navegador do celular consegue abrir o arquivo sem header JWT,
 * e um link vazado deixa de funcionar quando expira.
 */
@Component
public class MediaLinks {

	public static final String MEDIA_PREFIX = "/media/";

	private static MediaLinks instance;

	private final byte[] secret;
	private final long ttlSeconds;

	public MediaLinks(
		@Value("${app.jwt.secret}") String secret,
		@Value("${app.media.link-ttl-seconds:21600}") long ttlSeconds
	) {
		this.secret = ("media:" + secret).getBytes(StandardCharsets.UTF_8);
		this.ttlSeconds = ttlSeconds;
		instance = this;
	}

	/* Usado pelos mappers estaticos: assina "/media/{chave}"; outras URLs passam intactas. */
	public static String signed(String url) {
		if (url == null || !url.startsWith(MEDIA_PREFIX) || instance == null) {
			return url;
		}
		String key = url.substring(MEDIA_PREFIX.length());
		int query = key.indexOf('?');
		if (query >= 0) {
			key = key.substring(0, query);
		}
		return instance.sign(key);
	}

	public String sign(String key) {
		long exp = Instant.now().getEpochSecond() + ttlSeconds;
		return MEDIA_PREFIX + key + "?exp=" + exp + "&sig=" + hmac(key + ":" + exp);
	}

	public boolean verify(String key, Long exp, String sig) {
		if (key == null || exp == null || sig == null || exp < Instant.now().getEpochSecond()) {
			return false;
		}
		return MessageDigest.isEqual(hmac(key + ":" + exp).getBytes(StandardCharsets.UTF_8),
			sig.getBytes(StandardCharsets.UTF_8));
	}

	private String hmac(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Falha ao assinar link de mídia", exception);
		}
	}
}
