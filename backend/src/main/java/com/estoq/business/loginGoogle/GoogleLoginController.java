package com.estoq.business.loginGoogle;

import com.estoq.business.auth.AuthService;
import com.estoq.business.auth.LimiteRequisicoes;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class GoogleLoginController {
    private final GoogleLoginConfig config;
    private final GoogleLoginService google;
    private final GooglePendencias pendencias;
    private final AuthService auth;
    private final LimiteRequisicoes limites;
    @Value("${estoq.cadastro.max-tentativas:10}")
    private int maxCadastros;

    @GetMapping("/api/auth/google/configuracao")
    public Map<String, Boolean> configuracao() { return Map.of("enabled", config.isEnabled()); }

    @GetMapping("/api/auth/google/pendente")
    public ResponseEntity<GooglePendencias.Resumo> pendente(HttpServletRequest req) {
        google.exigirAtivo();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pendencias.resumo(req));
    }

    @PostMapping("/api/registro/google")
    public ResponseEntity<GoogleResultadoDTO> cadastrar(@Valid @RequestBody GoogleCadastroDTO dto,
            HttpServletRequest req, HttpServletResponse res) {
        limites.reservar("cadastro:" + req.getRemoteAddr(), maxCadastros, Duration.ofHours(1));
        var p = pendencias.obter(req);
        conferirEtapa(p, GooglePendencias.Etapa.CADASTRO);
        var usuario = google.cadastrar(p.identidade(), dto);
        return ResponseEntity.status(201).body(concluir(usuario, req, res));
    }

    @PostMapping("/api/auth/google/vincular")
    public GoogleResultadoDTO vincular(@Valid @RequestBody GoogleVinculoDTO dto,
            HttpServletRequest req, HttpServletResponse res) {
        limites.reservar("google-vinculo:" + req.getRemoteAddr(), 60, Duration.ofHours(1));
        var p = pendencias.obter(req);
        conferirEtapa(p, GooglePendencias.Etapa.VINCULAR);
        return concluir(google.vincular(p.identidade(), dto.senha(), req.getRemoteAddr()), req, res);
    }

    @PostMapping("/api/auth/google/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(HttpServletRequest req) { pendencias.limpar(req); }

    // Com a função ativa, o filtro OAuth2 trata estas URLs antes do controller.
    @GetMapping({"/api/oauth2/authorization/google", "/api/login/oauth2/code/google"})
    public void indisponivel() {
        throw new BusinessException("Acesso com Google temporariamente indisponível.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    private GoogleResultadoDTO concluir(UsuarioModel usuario, HttpServletRequest req, HttpServletResponse res) {
        pendencias.limpar(req);
        var sessao = usuario.emailPendente() ? null : auth.abrirSessao(usuario, req, res);
        return new GoogleResultadoDTO(sessao, usuario.emailPendente(), usuario.getEmail());
    }

    private void conferirEtapa(GooglePendencias.Pendente p, GooglePendencias.Etapa etapa) {
        if (p.etapa() != etapa) throw new BusinessException("Entre com Google novamente para continuar.", HttpStatus.BAD_REQUEST);
    }

}
