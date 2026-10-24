package com.estoq.business.consumos;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.lotes.BaixaLoteService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.lotes.LoteModel;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoRegistroService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;
import com.estoq.business.produtos.ProdutoService;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.estoq.business.produtosAbertos.ProdutoAbertoModel;
import java.math.BigDecimal;
import com.estoq.business.usuarios.UsuarioModel;

@Service
@RequiredArgsConstructor
public class ConsumoService {

    private final HorarioOperacao horario;

    private final ProdutoService produtos;
    private final LoteService lotes;
    private final ILoteRepository repository;
    private final BaixaLoteService baixa;
    private final UsuarioAtual usuarioAtual;
    private final MovimentacaoRegistroService registros;

    @Transactional
    public MovimentacaoResultadoDTO registrarConsumo(ConsumoRequestDTO d) {
        produtos.encontrar(d.produtoId());
        baixa.validarQuantidade(d.quantidade());
        List<LoteModel> candidatos = d.loteId() == null
                ? repository.findDisponiveisBaixa(d.produtoId(), horario.hoje())
                : List.of(lotes.encontrar(d.loteId()));
        var movs = baixarConsumo(d, candidatos, usuarioAtual.obter());
        return registros.concluir(d.produtoId(), movs);
    }

    /** Mesma regra para o consumo individual e conjunto; o chamador controla a transação. */
    List<ConsumoModel> baixarConsumo(ConsumoRequestDTO d, List<LoteModel> candidatos, UsuarioModel usuario) {
        return baixarConsumo(d, candidatos, usuario, null);
    }

    List<ConsumoModel> baixarConsumo(ConsumoRequestDTO d, List<LoteModel> candidatos, UsuarioModel usuario,
            Map<Long, List<ProdutoAbertoModel>> embalagensPorLote) {
        baixa.validarQuantidade(d.quantidade());
        var disponivel = candidatos.stream().map(LoteModel::getQuantidadeAtual)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (d.quantidade().compareTo(disponivel) > 0) {
            throw new ConflictException("Estoque disponível insuficiente para o consumo solicitado. Disponível: "
                    + disponivel.stripTrailingZeros().toPlainString() + ", solicitado: "
                    + d.quantidade().stripTrailingZeros().toPlainString() + ".");
        }
        var restante = d.quantidade();
        var movs = new ArrayList<ConsumoModel>();
        for (var lote : candidatos) {
            if (restante.signum() == 0) {
                break;
            }
            if (!lote.getProduto().getId().equals(d.produtoId())) {
                throw new FieldValidationException("loteId", "O lote não pertence ao produto.");
            }
            if (!lote.estaDisponivel(horario.hoje())) {
                throw new ConflictException("Lote vencido, sem validade ou indisponível para consumo.");
            }
            baixa.conferirVersao(lote, d.versionLote());
            var qtd = restante.min(lote.getQuantidadeAtual());
            // No consumo conjunto as embalagens já vieram em uma consulta única.
            var parcelas = embalagensPorLote == null ? baixa.baixar(lote, qtd)
                    : baixa.baixar(lote, qtd, embalagensPorLote.getOrDefault(lote.getId(), List.of()));
            for (var parcela : parcelas) {
                var c = new ConsumoModel();
                c.registrar(lote, usuario, parcela.quantidade(), parcela.anterior(), parcela.posterior(),
                        d.observacao(), parcela.aberto());
                registros.salvar(c);
                movs.add(c);
            }
            restante = restante.subtract(qtd);
        }
        if (restante.signum() > 0) {
            throw new ConflictException("Estoque disponível insuficiente para o consumo solicitado.");
        }
        return movs;
    }
}
