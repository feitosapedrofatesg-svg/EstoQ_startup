package com.estoq;

import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.consumos.ConsumoRequestDTO;
import com.estoq.business.consumos.ConsumoService;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.produtos.ProdutoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.patterns.RegistradorConsumoSingleton;

import jakarta.persistence.OptimisticLockException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("concorrencia")
class ConcorrenciaIntegrationTest {

    @Autowired
    IUsuarioRepository users;
    @Autowired
    ICategoriaRepository categorias;
    @Autowired
    ProdutoService produtos;
    @Autowired
    EntradaService entradas;
    @Autowired
    ConsumoService consumos;
    @Autowired
    ILoteRepository lotes;
    Long produtoId;

    static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, n(esperado).compareTo(valor));
    }

    @BeforeEach
    void preparar() {
        var u = new UsuarioModel();
        u.setNome("Operador");
        u.setEmail("concorrencia@test.local");
        u.setSenha("hash");
        u.setPerfil(Perfil.ADMIN);
        users.saveAndFlush(u);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, List.of()));
        var c = new CategoriaModel();
        c.setNome("Categoria");
        categorias.saveAndFlush(c);
        var d = new ProdutoDTO();
        d.setNome("Farinha");
        d.setCategoriaId(c.getId());
        d.setUnidadeMedida(UnidadeMedida.KG);
        produtoId = produtos.criar(d).getId();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void singletonComThreadsRegistraSomenteUmConsumo() throws Exception {
        Long loteId = entradas.registrarEntrada(new EntradaRequestDTO(produtoId, n("5"), n("50"), UnidadeMedida.KG, LocalDate.now().plusDays(60), null, false))
                .movimentacoes().getFirst().loteId();
        Long version = lotes.findById(loteId).orElseThrow().getVersion();

        var singleton = RegistradorConsumoSingleton.obter(consumos);
        assertSame(singleton, RegistradorConsumoSingleton.obter(consumos));

        var sucessos = new AtomicInteger();
        var conflitos = new AtomicInteger();
        Runnable consumir = () -> {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("concorrencia@test.local", null, List.of()));
            try {
                singleton.registrar(new ConsumoRequestDTO(produtoId, loteId, n("1"), version, null));
                sucessos.incrementAndGet();
            } catch (ConflictException | OptimisticLockingFailureException | OptimisticLockException e) {
                conflitos.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
        var t1 = new Thread(consumir);
        var t2 = new Thread(consumir);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(1, sucessos.get());
        assertEquals(1, conflitos.get());
        igual("4", lotes.findById(loteId).orElseThrow().getQuantidadeAtual());
        igual("4", lotes.saldo(produtoId));
    }
}