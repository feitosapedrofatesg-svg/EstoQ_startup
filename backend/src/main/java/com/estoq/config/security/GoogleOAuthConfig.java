package com.estoq.config.security;

import com.estoq.business.auth.AuthService;
import com.estoq.business.loginGoogle.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

/** O Spring cuida do protocolo; os serviços do EstoQ cuidam das contas. */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "estoq.login.google.enabled", havingValue = "true")
public class GoogleOAuthConfig {
    private final GoogleLoginConfig config;
    private final GoogleLoginService google;
    private final GooglePendencias pendencias;
    private final AuthService auth;
    private final SecurityContextRepository contextos;

    @Bean
    public ClientRegistrationRepository clientesGoogle() {
        var cliente = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(config.getClientId()).clientSecret(config.getClientSecret())
                .redirectUri(config.retorno()).scope("openid", "email", "profile").build();
        return new InMemoryClientRegistrationRepository(cliente);
    }

    @Bean
    public OAuth2AuthorizedClientRepository autorizacoesGoogle() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    public void configurar(HttpSecurity http, ClientRegistrationRepository clientes,
            OAuth2AuthorizedClientRepository autorizacoes) throws Exception {
        var pedidos = new DefaultOAuth2AuthorizationRequestResolver(clientes, "/api/oauth2/authorization");
        pedidos.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        var usuarioGoogle = new OidcUserService();
        // Google já entrega os dados necessários no ID token validado.
        usuarioGoogle.setRetrieveUserInfo(pedido -> false);
        http.oauth2Login(login -> login
                .clientRegistrationRepository(clientes).authorizedClientRepository(autorizacoes)
                .authorizationEndpoint(p -> p.authorizationRequestResolver(pedidos))
                .redirectionEndpoint(p -> p.baseUri("/api/login/oauth2/code/*"))
                .tokenEndpoint(p -> p.accessTokenResponseClient(clienteTokens()))
                .userInfoEndpoint(p -> p.oidcUserService(usuarioGoogle))
                .successHandler((req, res, autenticacao) -> concluir(req, res, autenticacao, autorizacoes))
                .failureHandler((req, res, erro) -> {
                    limpar(req, res);
                    boolean cancelado = erro instanceof OAuth2AuthenticationException e
                            && "access_denied".equals(e.getError().getErrorCode());
                    res.sendRedirect(config.getUrlPublica() + "/login?google=" + (cancelado ? "cancelado" : "erro"));
                }));
    }

    private RestClientAuthorizationCodeTokenResponseClient clienteTokens() {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        var rest = RestClient.builder().requestFactory(fabrica).configureMessageConverters(conversores -> conversores
                .disableDefaults().addCustomConverter(new FormHttpMessageConverter())
                .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        var cliente = new RestClientAuthorizationCodeTokenResponseClient();
        cliente.setRestClient(rest);
        return cliente;
    }

    private void concluir(HttpServletRequest req, HttpServletResponse res, Authentication autenticacao,
            OAuth2AuthorizedClientRepository autorizacoes) throws java.io.IOException {
        var oauth = (OAuth2AuthenticationToken) autenticacao;
        autorizacoes.removeAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), autenticacao, req, res);
        limpar(req, res);
        try {
            var identidade = GoogleIdentidade.de((OidcUser) autenticacao.getPrincipal());
            var destino = google.reconhecer(identidade);
            if (destino.etapa() != null) {
                pendencias.guardar(req, identidade, destino.etapa());
                res.sendRedirect(config.getUrlPublica() + "/registro/google");
            } else {
                auth.abrirSessao(destino.usuario(), req, res);
                res.sendRedirect(config.getUrlPublica() + "/");
            }
        } catch (RuntimeException e) {
            limpar(req, res);
            log.warn("Não foi possível concluir o acesso Google ({})", e.getClass().getSimpleName());
            res.sendRedirect(config.getUrlPublica() + "/login?google=erro");
        }
    }

    private void limpar(HttpServletRequest req, HttpServletResponse res) {
        SecurityContextHolder.clearContext();
        contextos.saveContext(SecurityContextHolder.createEmptyContext(), req, res);
        pendencias.limpar(req);
    }
}
