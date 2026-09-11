package com.estoq.business.lotes;

import com.estoq.business.produtosAbertos.IProdutoAbertoRepository;
import com.estoq.business.produtosAbertos.ProdutoAbertoModel;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Um único caminho de baixa mantém lote e parcelas abertas coerentes. */
@Service
@RequiredArgsConstructor
public class BaixaLoteService {

    private final IProdutoAbertoRepository abertos;

    public record Parcela(ProdutoAbertoModel aberto, BigDecimal quantidade, BigDecimal anterior, BigDecimal posterior) {
    }

    public void conferirVersao(LoteModel lote, Long version) {
        if (version != null && !Objects.equals(version, lote.getVersion())) {
            throw new ConflictException();
        }
    }

    public void validarQuantidade(BigDecimal qtd) {
        if (qtd == null || qtd.signum() <= 0 || qtd.stripTrailingZeros().scale() > 3) {
            throw new FieldValidationException("quantidade", "Informe quantidade positiva com até 3 casas decimais.");
        }
    }

    @Transactional
    public List<Parcela> baixar(LoteModel lote, BigDecimal quantidade) {
        validarQuantidade(quantidade);
        if (quantidade.compareTo(lote.getQuantidadeAtual()) > 0) {
            throw new ConflictException("Estoque insuficiente no lote.");
        }
        var parcelas = new ArrayList<Parcela>();
        var restante = quantidade;
        for (var aberto : abertos.findByLote_IdAndAtivoTrueAndFinalizadoFalseOrderByDataAberturaAscIdAsc(lote.getId())) {
            if (restante.signum() == 0) {
                break;
            }
            var qtd = restante.min(aberto.getQuantidadeRestante());
            if (qtd.signum() == 0) {
                continue;
            }
            var anterior = lote.getQuantidadeAtual();
            lote.baixar(qtd);
            aberto.utilizar(qtd);
            parcelas.add(new Parcela(aberto, qtd, anterior, lote.getQuantidadeAtual()));
            restante = restante.subtract(qtd);
        }
        if (restante.signum() > 0) {
            var anterior = lote.getQuantidadeAtual();
            lote.baixar(restante);
            parcelas.add(new Parcela(null, restante, anterior, lote.getQuantidadeAtual()));
        }
        return parcelas;
    }
}