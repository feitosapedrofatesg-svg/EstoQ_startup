package com.estoq.config.security;

import com.estoq.business.usuarios.IUsuarioRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource cors(@Value("${estoq.cors.origins:}") String origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http, SecurityExceptionHandlers errors,
            SecurityContextRepository context, CsrfTokenRepository csrf, CorsConfigurationSource cors,
            IUsuarioRepository users) throws Exception {
        return http.cors(c -> c.configurationSource(cors))
                .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(c -> c.securityContextRepository(context))
                .requestCache(c -> c.disable())
                .addFilterAfter(new UsuarioAtivoFilter(users), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/csrf", "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/produtos/**", "/api/lotes/**",
                                "/api/estoque", "/api/produtos-abertos/**", "/api/balancos/**", "/api/movimentacoes/**", "/api/alertas/**").hasAnyRole("ADMIN", "COZINHA")
                        .requestMatchers(HttpMethod.POST, "/api/consumos", "/api/desperdicios", "/api/produtos-abertos/abrir",
                                "/api/produtos-abertos/*/consumir", "/api/produtos-abertos/*/desperdicar", "/api/balancos", "/api/balancos/*/iniciar").hasAnyRole("ADMIN", "COZINHA")
                        .requestMatchers(HttpMethod.PUT, "/api/balancos/*/itens/*/contagem", "/api/alertas/*/visualizado").hasAnyRole("ADMIN", "COZINHA")
                        .requestMatchers(HttpMethod.GET, "/api/relatorios/**", "/api/dashboard/**", "/api/parametros-cmv").hasAnyRole("ADMIN", "NUTRICIONISTA")
                        .requestMatchers(HttpMethod.PUT, "/api/parametros-cmv").hasAnyRole("ADMIN", "NUTRICIONISTA")
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> errors.responder(res, 401, "Autenticação necessária."))
                        .accessDeniedHandler((req, res, ex) -> errors.responder(res, 403, "Operação não permitida ou token CSRF inválido.")))
                .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("ESTOQSESSION")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .build();
    }
}