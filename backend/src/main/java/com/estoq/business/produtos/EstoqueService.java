package com.estoq.business.produtos;

import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.produtosAbertos.IProdutoAbertoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.estoq.core.helpers.NumeroUtil.money;
import static com.estoq.core.helpers.NumeroUtil.s;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstoqueService {

    private final ILoteRepository lotes;
    private final IProdutoRepository produtos;
    private final IProdutoAbertoRepository abertos;

    /** RN02/RN11: restante aberto já está no lote; nunca somá-lo outra vez. */
    public BigDecimal obterSaldo(Long produtoId) {
        return s(lotes.saldo(produtoId));
    }

    public Map<Long, ILoteRepository.PosicaoProduto> posicoes() {
        return lotes.posicoes().stream().collect(Collectors.toMap(ILoteRepository.PosicaoProduto::getProdutoId, Function.identity()));
    }

    public List<EstoqueDTO> listar(boolean somenteAbertos, boolean somenteBaixo) {
        var saldos = posicoes();
        var idsAbertos = new HashSet<>(abertos.produtosComAbertos());
        return produtos.findAllByAtivoTrue().stream().map(p -> {
            var pos = saldos.get(p.getId());
            var par = p.getParametro();
            BigDecimal saldo = pos == null ? BigDecimal.ZERO : pos.getSaldo();
            BigDecimal minimo = par == null ? null : par.getEstoqueMinimo();
            BigDecimal medio = par == null ? null : par.getEstoqueMedio();
            BigDecimal maximo = par == null ? null : par.getEstoqueMaximo();
            boolean abaixo = par != null && minimo != null && saldo.compareTo(minimo) < 0;
            return new EstoqueDTO(p.getId(), p.getNome(), p.getCategoria().getNome(), p.getUnidadeMedida(), saldo,
                    money(pos == null ? null : pos.getValor()), minimo, medio, maximo, abaixo, idsAbertos.contains(p.getId()));
        }).filter(d -> !somenteAbertos || d.possuiItensAbertos()).filter(d -> !somenteBaixo || d.abaixoDoMinimo())
                .sorted(Comparator.comparing(EstoqueDTO::produtoNome, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}