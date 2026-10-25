package com.estoq.business.confirmacaoEmail;

import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.business.sincronizacao.DadosAlteradosService;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.helpers.HorarioOperacao;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional
public class ConfirmacaoEmailService {
    private final EmailConfig config;
    private final IConfirmacaoEmailRepository tokens;
    private final IUsuarioRepository usuarios;
    private final HorarioOperacao horario;
    private final ApplicationEventPublisher eventos;
    private final DadosAlteradosService sincronizacao;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Chamado apenas para cadastro ou mudança efetiva de endereço. */
    public void preparar(UsuarioModel usuario) {
        usuario.setConfirmacaoEmailObrigatoria(config.isEnabled() || usuario.isConfirmacaoEmailObrigatoria());
        usuario.setEmailConfirmadoEm(null);
    }

    public void solicitar(UsuarioModel usuario) {
        if (!config.isEnabled() || !usuario.isAtivo() || !usuario.emailPendente()) return;
        var agora = horario.agoraUtc();
        // O bloqueio do usuário serializa reenvios; o limite também sobrevive ao restart.
        if (tokens.countByUsuarioIdAndDataHoraCriacaoAfter(usuario.getId(), agora.minusMinutes(1)) > 0
                || tokens.countByUsuarioIdAndDataHoraCriacaoAfter(usuario.getId(), agora.minusHours(1)) >= 5) return;
        tokens.invalidarAnteriores(usuario.getId());
        var bytes = new byte[32]; ALEATORIO.nextBytes(bytes);
        var original = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new ConfirmacaoEmailModel();
        token.setUsuarioId(usuario.getId()); token.setEmail(usuario.getEmail());
        token.setTokenHash(hash(original)); token.setDataHoraCriacao(agora); token.setExpiraEm(agora.plusHours(24));
        tokens.saveAndFlush(token);
        eventos.publishEvent(new ConfirmacaoSolicitada(usuario.getId(), usuario.getEmail(), original));
    }

    public void reenviar(String email) {
        exigirEnvioDisponivel();
        usuarios.bloquearPorEmail(email.trim()).ifPresent(this::solicitar);
    }

    public void exigirEnvioDisponivel() {
        if (!config.isEnabled()) throw new BusinessException("Envio de e-mail temporariamente indisponível.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public void confirmar(String original) {
        var hash = hash(original);
        var encontrado = tokens.findByTokenHash(hash).orElseThrow(this::invalido);
        // Ordem de locks igual ao reenvio: primeiro usuário, depois token.
        var usuario = usuarios.bloquearPorId(encontrado.getUsuarioId()).orElseThrow(this::invalido);
        var token = tokens.bloquear(hash).orElseThrow(this::invalido);
        if (!token.isAtivo() || !usuario.isAtivo() || !usuario.emailPendente()
                || !token.getEmail().equalsIgnoreCase(usuario.getEmail())
                || !horario.agoraUtc().isBefore(token.getExpiraEm())) throw invalido();
        usuario.setEmailConfirmadoEm(horario.agoraUtc());
        token.setAtivo(false);
        usuarios.flush(); tokens.flush();
        sincronizacao.registrar(usuario);
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void limparExpirados() {
        tokens.deleteByExpiraEmBefore(horario.agoraUtc().minusDays(7));
    }

    static String hash(String original) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private BusinessException invalido() {
        return new BusinessException("Link inválido, vencido ou já utilizado. Solicite outro link de confirmação.", HttpStatus.BAD_REQUEST);
    }
}
