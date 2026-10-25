package com.estoq.business.loginGoogle;

import com.estoq.business.auth.AuthService;
import com.estoq.business.auth.LoginRequestDTO;
import com.estoq.business.auth.UsuarioPrincipal;
import com.estoq.business.confirmacaoEmail.ConfirmacaoEmailService;
import com.estoq.business.confirmacaoEmail.EmailConfig;
import com.estoq.business.registro.RegistroService;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.helpers.HorarioOperacao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import static com.estoq.business.loginGoogle.GooglePendencias.Etapa;

@Service
@RequiredArgsConstructor
public class GoogleLoginService {
    private final IUsuarioRepository usuarios;
    private final AuthService auth;
    private final RegistroService registro;
    private final ConfirmacaoEmailService confirmacao;
    private final EmailConfig emailConfig;
    private final GoogleLoginConfig config;
    private final HorarioOperacao horario;

    public record Destino(Etapa etapa, UsuarioModel usuario) {}

    public void exigirAtivo() {
        if (!config.isEnabled()) throw new BusinessException("Acesso com Google temporariamente indisponível.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Transactional
    public Destino reconhecer(GoogleIdentidade identidade) {
        exigirAtivo();
        var vinculado = usuarios.findByGoogleSub(identidade.sub()).orElse(null);
        if (vinculado != null) {
            vinculado = usuarios.bloquearPorId(vinculado.getId()).orElseThrow(() -> new BadCredentialsException("Conta indisponível."));
            auth.validarAcesso(vinculado);
            confirmarQuandoConfiavel(vinculado, identidade);
            if (vinculado.emailPendente()) {
                confirmacao.solicitar(vinculado);
                return new Destino(Etapa.CONFIRMAR_EMAIL, vinculado);
            }
            return new Destino(null, vinculado);
        }
        var existente = usuarios.findByEmailIgnoreCase(identidade.email()).orElse(null);
        if (existente != null) {
            auth.validarAcesso(existente);
            if (existente.getGoogleSub() != null || existente.getSenha() == null)
                throw new ConflictException("Essa conta já possui outro vínculo Google. Fale com o administrador da cozinha.");
            return new Destino(Etapa.VINCULAR, null);
        }
        conferirEnvio(identidade);
        return new Destino(Etapa.CADASTRO, null);
    }

    @Transactional
    public UsuarioModel cadastrar(GoogleIdentidade identidade, GoogleCadastroDTO dto) {
        exigirAtivo();
        conferirEnvio(identidade);
        if (usuarios.findByGoogleSub(identidade.sub()).isPresent())
            throw new ConflictException("Essa conta já foi cadastrada. Entre com Google novamente.");
        return registro.registrarGoogle(dto.nomeLoja(), dto.nomeResponsavel(), identidade);
    }

    @Transactional
    public UsuarioModel vincular(GoogleIdentidade identidade, String senha, String ip) {
        exigirAtivo();
        var autenticado = auth.validarSenha(new LoginRequestDTO(identidade.email(), senha), ip);
        var principal = (UsuarioPrincipal) autenticado.getPrincipal();
        var usuario = usuarios.bloquearPorId(principal.id()).orElseThrow(() -> new BadCredentialsException("Conta indisponível."));
        auth.validarAcesso(usuario);
        if (!usuario.getEmail().equalsIgnoreCase(identidade.email()) || !Objects.equals(usuario.getSenha(), principal.senha())
                || usuario.getGoogleSub() != null
                || usuarios.findByGoogleSub(identidade.sub()).isPresent())
            throw new ConflictException("O cadastro mudou. Entre com Google novamente.");
        usuario.setGoogleSub(identidade.sub());
        confirmarQuandoConfiavel(usuario, identidade);
        if (usuario.emailPendente()) confirmacao.solicitar(usuario);
        usuarios.flush();
        return usuario;
    }

    private void confirmarQuandoConfiavel(UsuarioModel usuario, GoogleIdentidade identidade) {
        if (identidade.emailConfiavel() && usuario.getEmail().equalsIgnoreCase(identidade.email())
                && usuario.getEmailConfirmadoEm() == null) usuario.setEmailConfirmadoEm(horario.agoraUtc());
    }

    private void conferirEnvio(GoogleIdentidade identidade) {
        if (!identidade.emailConfiavel() && !emailConfig.isEnabled())
            throw new BusinessException("Não foi possível confirmar esse endereço. Use uma conta Gmail ou tente novamente mais tarde.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
