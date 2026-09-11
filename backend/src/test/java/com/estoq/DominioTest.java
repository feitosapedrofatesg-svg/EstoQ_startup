package com.estoq;

import com.estoq.business.lotes.LoteModel;
import com.estoq.business.parametrosEstoque.ParametroEstoqueModel;
import com.estoq.business.parametrosEstoque.ParametroEstoqueValidation;
import com.estoq.business.produtos.ConversorUnidade;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.produtos.UnidadeMedida;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

class DominioTest {

    @Test
    void converteMassaEVolumeSemMisturarDimensoes() {
        assertEquals(new BigDecimal("1000.000"), ConversorUnidade.converter(BigDecimal.ONE, UnidadeMedida.KG, UnidadeMedida.G));
        assertEquals(new BigDecimal("1.000"), ConversorUnidade.converter(new BigDecimal("1000"), UnidadeMedida.G, UnidadeMedida.KG));
        assertEquals(new BigDecimal("1500.000"), ConversorUnidade.converter(new BigDecimal("1.5"), UnidadeMedida.L, UnidadeMedida.ML));
        assertEquals(new BigDecimal("0.500"), ConversorUnidade.converter(new BigDecimal("500"), UnidadeMedida.ML, UnidadeMedida.L));
        assertThrows(FieldValidationException.class, () -> ConversorUnidade.converter(BigDecimal.ONE, UnidadeMedida.UN, UnidadeMedida.KG));
        assertThrows(FieldValidationException.class, () -> ConversorUnidade.converter(BigDecimal.ONE, UnidadeMedida.KG, UnidadeMedida.L));
    }

    @Test
    void baixaNuncaDeixaLoteNegativo() {
        var l = new LoteModel();
        l.setQuantidadeAtual(new BigDecimal("2"));
        l.setCodigo("TESTE");
        assertThrows(ConflictException.class, () -> l.baixar(new BigDecimal("3")));
        assertEquals(new BigDecimal("2"), l.getQuantidadeAtual());
    }

    @Test
    void validadeHojeDisponivelOntemVencido() {
        var l = new LoteModel();
        l.setProduto(new ProdutoModel());
        l.setQuantidadeAtual(BigDecimal.ONE);
        l.setDataValidade(LocalDate.now());
        assertTrue(l.estaDisponivel(LocalDate.now()));
        l.setDataValidade(LocalDate.now().minusDays(1));
        assertFalse(l.estaDisponivel(LocalDate.now()));
        assertTrue(l.estaVencido(LocalDate.now()));
    }

    @Test
    void rejeitaNiveisDeEstoqueInvertidos() {
        var p = new ParametroEstoqueModel();
        p.setProduto(new ProdutoModel());
        p.setEstoqueMinimo(new BigDecimal("10"));
        assertThrows(FieldValidationException.class, () -> new ParametroEstoqueValidation().validate(p));
        p.setEstoqueMedio(new BigDecimal("20"));
        p.setEstoqueMaximo(new BigDecimal("30"));
        assertDoesNotThrow(() -> new ParametroEstoqueValidation().validate(p));
    }
}