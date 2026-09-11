package com.estoq.business.entradas;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoRegistroService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;
import com.estoq.business.produtos.ConversorUnidade;
import com.estoq.business.produtos.ProdutoService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntradaService {

    private final ProdutoService produtos;
    private final LoteService lotes;
    private final UsuarioAtual usuarioAtual;
    private final EntradaValidation validation;
    private final MovimentacaoRegistroService registros;

    @Transactional
    public MovimentacaoResultadoDTO registrarEntrada(EntradaRequestDTO d) {
        validation.validar(d);
        var semCusto = Boolean.TRUE.equals(d.semCusto());
        var produto = produtos.encontrar(d.produtoId());
        var quantidade = ConversorUnidade.converter(d.quantidade(), d.unidadeCompra(), produto.getUnidadeMedida());
        var preco = semCusto ? BigDecimal.ZERO : d.valorTotalPago().divide(quantidade, 6, RoundingMode.HALF_UP);
        var lote = lotes.criar(produto, quantidade, quantidade, preco, d.dataValidade());
        var e = new EntradaModel();
        e.setValorTotalPago(semCusto ? BigDecimal.ZERO : d.valorTotalPago());
        e.setUnidadeCompra(d.unidadeCompra());
        e.setDataValidade(d.dataValidade());
        e.registrar(lote, usuarioAtual.obter(), quantidade, BigDecimal.ZERO, quantidade, d.observacao(), null);
        registros.salvar(e);
        return registros.concluir(produto.getId(), List.of(e));
    }
}