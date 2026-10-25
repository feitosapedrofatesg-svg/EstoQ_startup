package com.estoq.business.registro;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.categorias.CategoriaPadraoService;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoModel;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.configuracoesBalanco.PeriodicidadeBalanco;
import com.estoq.business.parametrosCmv.IParametroCmvRepository;
import com.estoq.business.parametrosCmv.ParametroCmvModel;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.restaurantes.RestauranteModel;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.business.loginGoogle.GoogleIdentidade;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Abre o restaurante com seu primeiro ADMIN e os parâmetros padrão à loja,
 * tudo na mesma transação. O usuário passa a existir dentro do próprio tenant.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    private final HorarioOperacao horario;

    private final IRestauranteRepository restaurantes;
    private final IUsuarioRepository usuarios;
    private final IParametroCmvRepository parametrosCmv;
    private final IConfiguracaoBalancoRepository configuracoesBalanco;
    private final PasswordEncoder encoder;
    private final CategoriaPadraoService categoriasPadrao;
    private final com.estoq.business.confirmacaoEmail.ConfirmacaoEmailService confirmacaoEmail;

    @Transactional
    public boolean registrar(RegistroRequestDTO dto) {
        return criar(dto.nomeLoja(), dto.nomeResponsavel(), dto.email(), encoder.encode(dto.senha()), null, false).emailPendente();
    }

    @Transactional
    public UsuarioModel registrarGoogle(String nomeLoja, String nomeResponsavel, GoogleIdentidade identidade) {
        return criar(nomeLoja, nomeResponsavel, identidade.email(), null, identidade.sub(), identidade.emailConfiavel());
    }

    private UsuarioModel criar(String nomeLoja, String nomeResponsavel, String email, String senha,
            String googleSub, boolean emailConfiavel) {
        email = email.trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado. Faça o login ou use outro e-mail.");
        }
        var restaurante = new RestauranteModel();
        restaurante.setNome(nomeLoja.trim());
        restaurantes.saveAndFlush(restaurante);

        var admin = new UsuarioModel();
        admin.setNome(nomeResponsavel.trim());
        admin.setEmail(email);
        admin.setSenha(senha);
        admin.setGoogleSub(googleSub);
        admin.setPerfil(Perfil.ADMIN);
        admin.setRestauranteId(restaurante.getId());
        confirmacaoEmail.preparar(admin);
        if (googleSub != null) {
            admin.setConfirmacaoEmailObrigatoria(true);
            if (emailConfiavel) admin.setEmailConfirmadoEm(horario.agoraUtc());
        }
        usuarios.saveAndFlush(admin);

        criarParametroCmv(restaurante.getId());
        criarConfiguracaoBalanco(restaurante.getId());
        categoriasPadrao.inicializar(restaurante.getId());
        confirmacaoEmail.solicitar(admin);
        return admin;
    }

    private void criarParametroCmv(Long restauranteId) {
        var cmv = new ParametroCmvModel();
        cmv.setPercentualIdeal(new BigDecimal("30.00"));
        cmv.setDataAtualizacao(LocalDateTime.now(ZoneOffset.UTC));
        cmv.setRestauranteId(restauranteId);
        parametrosCmv.saveAndFlush(cmv);
    }

    private void criarConfiguracaoBalanco(Long restauranteId) {
        var config = new ConfiguracaoBalancoModel();
        config.setPeriodicidade(PeriodicidadeBalanco.MENSAL);
        config.setDiaExecucao(1);
        config.setProximaExecucao(horario.hoje().withDayOfMonth(1).plusMonths(1));
        config.setRestauranteId(restauranteId);
        configuracoesBalanco.saveAndFlush(config);
    }
}
