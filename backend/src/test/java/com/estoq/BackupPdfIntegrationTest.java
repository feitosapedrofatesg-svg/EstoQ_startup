package com.estoq;

import com.estoq.backup.BackupService;
import com.estoq.business.categorias.CategoriaModel;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.produtos.ProdutoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.business.relatorios.RelatorioPdfService;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.exceptions.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BackupPdfIntegrationTest {

    @Autowired
    IUsuarioRepository users;
    @Autowired
    ICategoriaRepository categorias;
    @Autowired
    ProdutoService produtos;
    @Autowired
    EntradaService entradas;
    @Autowired
    RelatorioPdfService pdfs;
    @Autowired
    BackupService backups;
    Long produtoId;

    @BeforeEach
    void preparar() {
        var u = new UsuarioModel();
        u.setNome("Relator");
        u.setEmail("relator@test.local");
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
        entradas.registrarEntrada(new EntradaRequestDTO(produtoId, new BigDecimal("10"), new BigDecimal("80"), UnidadeMedida.KG,
                LocalDate.now().plusDays(30), null, false));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void geraRelatorioPdfValido() {
        var bytes = pdfs.gerar("estoque-atual", LocalDate.now().minusDays(7), LocalDate.now(), 30, null);
        assertTrue(bytes.length > 100);
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void geraCmvPdfMesmoSemReceita() {
        var bytes = pdfs.gerar("cmv", LocalDate.now().minusDays(7), LocalDate.now(), 30, null);
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void rejeitaTipoDesconhecido() {
        assertThrows(BusinessException.class, () -> pdfs.gerar("inexistente", LocalDate.now(), LocalDate.now(), 30, null));
    }

    @Test
    void listaBackupsSemArquivos() {
        assertTrue(backups.listar().isEmpty());
        assertThrows(BusinessException.class, () -> backups.baixar("../etc/passwd"));
        assertEquals(0, backups.listar().size());
    }
}