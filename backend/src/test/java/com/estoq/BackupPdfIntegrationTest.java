package com.estoq;

import static com.estoq.core.helpers.HorarioOperacao.FUSO;

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
    @Autowired
    com.estoq.business.desperdicios.DesperdicioService desperdicios;
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
                LocalDate.now(FUSO).plusDays(30), null, false));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void geraRelatorioPdfValido() {
        var bytes = pdfs.gerar("estoque-atual", LocalDate.now(FUSO).minusDays(7), LocalDate.now(FUSO), 30, null);
        assertTrue(bytes.length > 100);
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void geraCmvPdfMesmoSemReceita() {
        var bytes = pdfs.gerar("cmv", LocalDate.now(FUSO).minusDays(7), LocalDate.now(FUSO), 30, null);
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    }

    @Test
    void todosOsTiposMantemFormatoPdfEPeriodo() throws Exception {
        for (var tipo : List.of("estoque-atual", "proximos-vencimento", "vencidos", "produtos-abertos", "desperdicio", "consumo-medio", "consumo-dia-semana", "faturamento", "cmv", "cmv-mensal", "reposicao-sugerida")) {
            var bytes = pdfs.gerar(tipo, LocalDate.now(FUSO).minusDays(7), LocalDate.now(FUSO), 8, new BigDecimal("1000"));
            String pasta = System.getProperty("estoq.pdf.validacao");
            if (pasta != null) {
                java.nio.file.Files.createDirectories(java.nio.file.Path.of(pasta));
                java.nio.file.Files.write(java.nio.file.Path.of(pasta, "integracao-" + tipo + ".pdf"), bytes);
            }
            try (var documento = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                var texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
                assertTrue(texto.contains("EstoQ"), tipo);
                assertTrue(texto.contains("Emissão:"), tipo);
                assertTrue(texto.contains("Página 1 de"), tipo);
                assertTrue(texto.contains("Posição atual") || texto.contains("Período:"), tipo);
            }
        }
    }

    @Test
    void exportaPerdaRegistradaSemDescricaoComplementar() throws Exception {
        desperdicios.registrarDesperdicio(new com.estoq.business.desperdicios.DesperdicioRequestDTO(
                produtoId, null, new BigDecimal("2"), com.estoq.business.desperdicios.MotivoDesperdicio.DETERIORACAO, null, null, null));
        var bytes = pdfs.gerar("desperdicio", LocalDate.now(FUSO).minusDays(1), LocalDate.now(java.time.ZoneOffset.UTC), 2, null);
        try (var documento = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            var texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
            assertTrue(texto.contains("Farinha"));
            assertTrue(texto.contains("DETERIORACAO"));
            assertTrue(texto.contains("2 kg"));
            assertTrue(texto.contains("Prejuízo total no período"));
            assertTrue(texto.contains("R$ 16,00"));
        }
    }

    @Test
    void rejeitaTipoDesconhecido() {
        assertThrows(BusinessException.class, () -> pdfs.gerar("inexistente", LocalDate.now(FUSO), LocalDate.now(FUSO), 30, null));
    }

    @Test
    void listaBackupsSemArquivos() {
        assertTrue(backups.listar().isEmpty());
        assertThrows(BusinessException.class, () -> backups.baixar("../etc/passwd"));
        assertEquals(0, backups.listar().size());
    }
}
