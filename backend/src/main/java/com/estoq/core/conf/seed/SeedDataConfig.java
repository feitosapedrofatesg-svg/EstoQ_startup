package com.estoq.core.conf.seed;

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

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Dados mínimos de desenvolvimento. Alterar as senhas padrão antes de qualquer uso real. */
@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class SeedDataConfig {

    private final IUsuarioRepository usuarios;
    private final IRestauranteRepository restaurantes;
    private final IParametroCmvRepository parametroCmv;
    private final IConfiguracaoBalancoRepository configuracoes;
    private final PasswordEncoder encoder;

    @Bean
    public CommandLineRunner seed(@Value("${estoq.seed.enabled:false}") boolean habilitado) {
        return args -> {
            if (!habilitado) {
                return;
            }
            Long tenant = restaurantePadrao().getId();
            criarUsuario(tenant, "admin@estoq.com", "Admin@12345", Perfil.ADMIN);
            criarUsuario(tenant, "cozinha@estoq.com", "Cozinha@12345", Perfil.COZINHA);
            criarUsuario(tenant, "nutricionista@estoq.com", "Nutricao@12345", Perfil.NUTRICIONISTA);
            if (parametroCmv.findFirstByAtivoTrueOrderByIdDesc().isEmpty()) {
                var cmv = new ParametroCmvModel();
                cmv.setPercentualIdeal(new BigDecimal("30.00"));
                cmv.setDataAtualizacao(LocalDateTime.now());
                cmv.setRestauranteId(tenant);
                parametroCmv.saveAndFlush(cmv);
            }
            if (configuracoes.findAllByAtivoTrueOrderByIdAsc().isEmpty()) {
                var config = new ConfiguracaoBalancoModel();
                config.setPeriodicidade(PeriodicidadeBalanco.MENSAL);
                config.setDiaExecucao(1);
                config.setProximaExecucao(LocalDate.now().withDayOfMonth(1).plusMonths(1));
                config.setRestauranteId(tenant);
                configuracoes.saveAndFlush(config);
            }
        };
    }

    /** Garante o tenant raiz de desenvolvimento e devolve seu id. */
    private RestauranteModel restaurantePadrao() {
        return restaurantes.findAllByAtivoTrue().stream().findFirst()
                .orElseGet(() -> {
                    var restaurante = new RestauranteModel();
                    restaurante.setNome("EstoQ Padrão");
                    return restaurantes.saveAndFlush(restaurante);
                });
    }

    private void criarUsuario(Long tenant, String email, String senha, Perfil perfil) {
        if (usuarios.existsByEmailIgnoreCase(email)) {
            return;
        }
        var usuario = new UsuarioModel();
        usuario.setNome(perfil.name().toLowerCase() + " estoq");
        usuario.setEmail(email);
        usuario.setSenha(encoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setRestauranteId(tenant);
        usuarios.saveAndFlush(usuario);
    }
}