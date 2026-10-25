package com.estoq;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Servidor local para exercitar o protocolo OIDC, sem chamar contas Google reais. */
class GoogleProvedorTeste implements AutoCloseable {
    static final String CLIENTE = "cliente-web-de-teste";
    static final String ORIGEM = "https://estoq.test";
    final HttpServer servidor;
    final RSAKey chave;
    final RSAKey outraChave;
    final ObjectMapper json = new ObjectMapper();
    final Map<String, Map<String, Object>> codigos = new ConcurrentHashMap<>();
    volatile Map<String, String> ultimoPedido = Map.of();

    GoogleProvedorTeste() {
        try {
            chave = new RSAKeyGenerator(2048).keyID("chave-teste").generate();
            outraChave = new RSAKeyGenerator(2048).keyID("chave-teste").generate();
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/chaves", pedido -> {
                var bytes = new JWKSet(chave.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                pedido.getResponseHeaders().set("Content-Type", "application/json");
                pedido.sendResponseHeaders(200, bytes.length);
                try (var corpo = pedido.getResponseBody()) { corpo.write(bytes); }
            });
            servidor.createContext("/token", pedido -> {
                try {
                    ultimoPedido = parametros(new String(pedido.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var claims = new HashMap<>(codigos.remove(ultimoPedido.get("code")));
                    boolean assinaturaErrada = Boolean.TRUE.equals(claims.remove("assinaturaErrada"));
                    var dados = new JWTClaimsSet.Builder();
                    claims.forEach(dados::claim);
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("chave-teste").build(), dados.build());
                    jwt.sign(new RSASSASigner(assinaturaErrada ? outraChave : chave));
                    var bytes = json.writeValueAsBytes(Map.of("access_token", "acesso-somente-teste", "token_type", "Bearer",
                            "expires_in", 3600, "scope", "openid email profile", "id_token", jwt.serialize()));
                    pedido.getResponseHeaders().set("Content-Type", "application/json");
                    pedido.sendResponseHeaders(200, bytes.length);
                    try (var corpo = pedido.getResponseBody()) { corpo.write(bytes); }
                } catch (Exception e) {
                    pedido.sendResponseHeaders(400, -1); pedido.close();
                }
            });
            servidor.start();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    ClientRegistration cliente() {
        var base = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(CLIENTE).clientSecret("segredo-de-teste")
                .redirectUri(ORIGEM + "/api/login/oauth2/code/google")
                .tokenUri(base + "/token").jwkSetUri(base + "/chaves").scope("openid", "email", "profile").build();
    }

    Map<String, Object> claims(String sub, String email, String nonce) {
        var agora = Instant.now();
        var claims = new HashMap<String, Object>();
        claims.put("iss", "https://accounts.google.com"); claims.put("sub", sub); claims.put("aud", CLIENTE);
        claims.put("iat", Date.from(agora)); claims.put("exp", Date.from(agora.plusSeconds(300)));
        claims.put("nonce", nonce); claims.put("email", email); claims.put("email_verified", true); claims.put("name", "Pessoa teste");
        return claims;
    }

    static Map<String, String> parametros(String texto) {
        var resultado = new HashMap<String, String>();
        for (String parte : texto.split("&")) {
            var par = parte.split("=", 2);
            resultado.put(URLDecoder.decode(par[0], StandardCharsets.UTF_8),
                    par.length == 2 ? URLDecoder.decode(par[1], StandardCharsets.UTF_8) : "");
        }
        return resultado;
    }

    public void close() { servidor.stop(0); }
}
