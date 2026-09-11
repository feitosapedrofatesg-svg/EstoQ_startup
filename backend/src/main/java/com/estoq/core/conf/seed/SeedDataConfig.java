package com.estoq.core.conf.seed;

import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoModel;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.configuracoesBalanco.PeriodicidadeBalanco;
import com.estoq.business.parametrosCmv.IParametroCmvRepository;
import com.estoq.business.parametrosCmv.ParametroCmvModel;
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
    private final IParametroCmvRepository parametroCmv;
    private final IConfiguracaoBalancoRepository configuracoes;
    private final PasswordEncoder encoder;

    @Bean
    public CommandLineRunner seed(@Value("${estoq.seed.enabled:false}") boolean habilitado) {
        return args -> {
            if (!habilitado) {
                return;
            }
            criarUsuario("admin@estoq.com", "Admin@12345", Perfil.ADMIN);
            criarUsuario("cozinha@estoq.com", "Cozinha@12345", Perfil.COZINHA);
            criarUsuario("nutricionista@estoq.com", "Nutricao@12345", Perfil.NUTRICIONISTA);
            if (parametroCmv.findFirstByAtivoTrueOrderByIdDesc().isEmpty()) {
                var cmv = new ParametroCmvModel();
                cmv.setPercentualIdeal(new BigDecimal("30.00"));
                cmv.setDataAtualizacao(LocalDateTime.now());
                parametroCmv.saveAndFlush(cmv);
            }
            if (configuracoes.findAllByAtivoTrueOrderByIdAsc().isEmpty()) {
                var config = new ConfiguracaoBalancoModel();
                config.setPeriodicidade(PeriodicidadeBalanco.MENSAL);
                config.setDiaExecucao(1);
                config.setProximaExecucao(LocalDate.now().withDayOfMonth(1).plusMonths(1));
                configuracoes.saveAndFlush(config);
            }
        };
    }

    private void criarUsuario(String email, String senha, Perfil perfil) {
        if (usuarios.existsByEmailIgnoreCase(email)) {
            return;
        }
        var usuario = new UsuarioModel();
        usuario.setNome(perfil.name().toLowerCase() + " estoq");
        usuario.setEmail(email);
        usuario.setSenha(encoder.encode(senha));
        usuario.setPerfil(perfil);
        usuarios.saveAndFlush(usuario);
    }
}