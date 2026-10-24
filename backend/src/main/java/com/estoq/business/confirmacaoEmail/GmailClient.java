package com.estoq.business.confirmacaoEmail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/** Gmail via HTTPS. Apenas renova a autorização do remetente e envia a mensagem. */
@Component
public class GmailClient {
    private final EmailConfig config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI autorizacao;
    private final URI envio;
    private String accessToken;
    private Instant validoAte = Instant.EPOCH;

    @Autowired
    public GmailClient(EmailConfig config, ObjectMapper json) {
        this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create("https://oauth2.googleapis.com/token"),
                URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"));
    }

    GmailClient(EmailConfig config, ObjectMapper json, HttpClient http, URI autorizacao, URI envio) {
        this.config = config; this.json = json; this.http = http;
        this.autorizacao = autorizacao; this.envio = envio;
    }

    public void enviar(String destinatario, String texto) throws IOException, InterruptedException {
        var mime = "From: EstoQ <" + endereco(config.getRemetente()) + ">\r\n"
                + "To: " + endereco(destinatario) + "\r\nSubject: Confirme seu e-mail no EstoQ\r\n"
                + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                + Base64.getMimeEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
        var corpo = json.writeValueAsString(Map.of("raw", raw));
        var resposta = enviarMensagem(corpo, token(false));
        if (resposta.statusCode() == 401) resposta = enviarMensagem(corpo, token(true));
        if (resposta.statusCode() / 100 != 2) throw new IOException("Gmail não aceitou o envio: HTTP " + resposta.statusCode());
    }

    private HttpResponse<Void> enviarMensagem(String corpo, String token) throws IOException, InterruptedException {
        var pedido = HttpRequest.newBuilder(envio).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        return http.send(pedido, HttpResponse.BodyHandlers.discarding());
    }

    private synchronized String token(boolean renovar) throws IOException, InterruptedException {
        if (!renovar && accessToken != null && Instant.now().isBefore(validoAte)) return accessToken;
        var campos = Map.of("client_id", config.getClientId(), "client_secret", config.getClientSecret(),
                "refresh_token", config.getRefreshToken(), "grant_type", "refresh_token");
        var corpo = campos.entrySet().stream().map(e -> codificar(e.getKey()) + "=" + codificar(e.getValue()))
                .collect(Collectors.joining("&"));
        var pedido = HttpRequest.newBuilder(autorizacao).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        var resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() != 200) throw new IOException("Autorização do Gmail indisponível: HTTP " + resposta.statusCode());
        var dados = json.readTree(resposta.body());
        var novo = dados.path("access_token").asText("");
        if (novo.isBlank()) throw new IOException("Autorização do Gmail sem token de acesso.");
        accessToken = novo;
        validoAte = Instant.now().plusSeconds(Math.max(0, dados.path("expires_in").asLong(3600) - 60));
        return accessToken;
    }

    private static String codificar(String valor) { return URLEncoder.encode(valor, StandardCharsets.UTF_8); }
    private static String endereco(String valor) {
        if (valor == null || !valor.matches("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9.-]+"))
            throw new IllegalArgumentException("Endereço de e-mail inválido.");
        return valor;
    }
}
