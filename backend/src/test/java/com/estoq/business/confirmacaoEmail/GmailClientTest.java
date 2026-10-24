package com.estoq.business.confirmacaoEmail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailClientTest {
    private HttpServer servidor;

    @AfterEach void encerrar() { if (servidor != null) servidor.stop(0); }

    @Test void renovaTokenNo401EEnviaMimeSeguroSemReautenticarEmCadaMensagem() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var renovacoes = new AtomicInteger();
        var mensagens = new AtomicInteger();
        var ultimoCorpo = new AtomicReference<String>();
        servidor.createContext("/token", pedido -> {
            renovacoes.incrementAndGet();
            var form = new String(pedido.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(form.contains("grant_type=refresh_token"));
            var corpo = "{\"access_token\":\"acesso-ficticio\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            pedido.sendResponseHeaders(200, corpo.length); pedido.getResponseBody().write(corpo); pedido.close();
        });
        servidor.createContext("/send", pedido -> {
            assertEquals("Bearer acesso-ficticio", pedido.getRequestHeaders().getFirst("Authorization"));
            ultimoCorpo.set(new String(pedido.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            pedido.sendResponseHeaders(mensagens.incrementAndGet() == 1 ? 401 : 200, -1); pedido.close();
        });
        servidor.start();
        var config = mock(EmailConfig.class);
        when(config.getRemetente()).thenReturn("estoq.sistema@gmail.com");
        when(config.getClientId()).thenReturn("cliente-ficticio");
        when(config.getClientSecret()).thenReturn("segredo-ficticio");
        when(config.getRefreshToken()).thenReturn("refresh-ficticio");
        var json = JsonMapper.builder().build();
        var base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        var gmail = new GmailClient(config, json, HttpClient.newHttpClient(), URI.create(base + "/token"), URI.create(base + "/send"));
        gmail.enviar("pessoa@teste.local", "Confirmação com acentuação e link de teste");
        gmail.enviar("pessoa@teste.local", "Confirmação com acentuação e link de teste");
        assertEquals(2, renovacoes.get()); assertEquals(3, mensagens.get());
        var raw = json.readTree(ultimoCorpo.get()).get("raw").asText();
        var mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
        assertTrue(mime.startsWith("From: EstoQ <estoq.sistema@gmail.com>\r\nTo: pessoa@teste.local\r\n"));
        var texto = new String(Base64.getMimeDecoder().decode(mime.split("\r\n\r\n", 2)[1]), StandardCharsets.UTF_8);
        assertEquals("Confirmação com acentuação e link de teste", texto);
        assertThrows(IllegalArgumentException.class, () -> gmail.enviar("pessoa@teste.local\r\nBcc: outra@teste.local", "Teste"));
    }
}
