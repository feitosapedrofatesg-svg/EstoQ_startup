package com.estoq.business.registro;

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

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Abre o restaurante com seu primeiro ADMIN e os parâmetros padrão à loja,
 * tudo na mesma transação. O usuário passa a existir dentro do próprio tenant.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    private final IRestauranteRepository restaurantes;
    private final IUsuarioRepository usuarios;
    private final IParametroCmvRepository parametrosCmv;
    private final IConfiguracaoBalancoRepository configuracoesBalanco;
    private final PasswordEncoder encoder;

    @Transactional
    public void registrar(RegistroRequestDTO dto) {
        if (usuarios.existsByEmailIgnoreCase(dto.email())) {
            throw new ConflictException("E-mail já cadastrado. Faça o login ou use outro e-mail.");
        }
        var restaurante = new RestauranteModel();
        restaurante.setNome(dto.nomeLoja().trim());
        restaurantes.saveAndFlush(restaurante);

        var admin = new UsuarioModel();
        admin.setNome(dto.nomeResponsavel().trim());
        admin.setEmail(dto.email().trim().toLowerCase(Locale.ROOT));
        admin.setSenha(encoder.encode(dto.senha()));
        admin.setPerfil(Perfil.ADMIN);
        admin.setRestauranteId(restaurante.getId());
        usuarios.saveAndFlush(admin);

        criarParametroCmv(restaurante.getId());
        criarConfiguracaoBalanco(restaurante.getId());
    }

    private void criarParametroCmv(Long restauranteId) {
        var cmv = new ParametroCmvModel();
        cmv.setPercentualIdeal(new BigDecimal("30.00"));
        cmv.setDataAtualizacao(LocalDateTime.now());
        cmv.setRestauranteId(restauranteId);
        parametrosCmv.saveAndFlush(cmv);
    }

    private void criarConfiguracaoBalanco(Long restauranteId) {
        var config = new ConfiguracaoBalancoModel();
        config.setPeriodicidade(PeriodicidadeBalanco.MENSAL);
        config.setDiaExecucao(1);
        config.setProximaExecucao(LocalDate.now().withDayOfMonth(1).plusMonths(1));
        config.setRestauranteId(restauranteId);
        configuracoesBalanco.saveAndFlush(config);
    }
}