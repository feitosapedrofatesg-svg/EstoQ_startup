package com.estoq.business.produtos;

import com.estoq.core.helpers.HorarioOperacao;

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

    private final HorarioOperacao horario;

    private final ILoteRepository lotes;
    private final IProdutoRepository produtos;
    private final IProdutoAbertoRepository abertos;
    private final com.estoq.business.lotes.LoteService lotesService;

    /** RN02/RN11: restante aberto já está no lote; nunca somá-lo outra vez. */
    public BigDecimal obterSaldo(Long produtoId) {
        return s(lotes.saldo(produtoId));
    }

    public Map<Long, ILoteRepository.PosicaoProduto> posicoes() {
        return lotes.posicoes().stream()
                .collect(Collectors.toMap(ILoteRepository.PosicaoProduto::getProdutoId, Function.identity()));
    }

    public Map<Long, ILoteRepository.PosicaoProduto> posicoesDisponiveis() {
        return lotes.posicoesDisponiveis(horario.hoje()).stream()
                .collect(Collectors.toMap(ILoteRepository.PosicaoProduto::getProdutoId, Function.identity()));
    }

    public List<EstoqueDTO> listar(boolean somenteAbertos, boolean somenteBaixo) {
        return adaptar(produtos.findAllByAtivoTrue(), somenteAbertos, somenteBaixo);
    }

    private List<EstoqueDTO> adaptar(List<ProdutoModel> lista, boolean somenteAbertos, boolean somenteBaixo) {
        var saldos = posicoes();
        var vencidos = lotesService.vencidos().stream().map(l -> l.produtoId()).collect(Collectors.toSet());
        var proximos = lotesService.proximosVencimento().stream().map(l -> l.produtoId()).collect(Collectors.toSet());
        var disponiveis = lotes.posicoesDisponiveis(horario.hoje()).stream()
                .collect(Collectors.toMap(ILoteRepository.PosicaoProduto::getProdutoId,
                        ILoteRepository.PosicaoProduto::getSaldo));
        var idsAbertos = new HashSet<>(abertos.produtosComAbertos());
        return lista.stream().map(p -> {
            var pos = saldos.get(p.getId());
            var par = p.getParametro();
            BigDecimal saldo = pos == null ? BigDecimal.ZERO : pos.getSaldo();
            BigDecimal minimo = par == null ? null : par.getEstoqueMinimo();
            BigDecimal medio = par == null ? null : par.getEstoqueMedio();
            BigDecimal maximo = par == null ? null : par.getEstoqueMaximo();
            boolean abaixo = par != null && minimo != null && disponiveis.getOrDefault(p.getId(), BigDecimal.ZERO).compareTo(minimo) < 0;
            return new EstoqueDTO(p.getId(), p.getNome(), p.getCategoria().getNome(), p.getUnidadeMedida(), saldo,
                    disponiveis.getOrDefault(p.getId(), BigDecimal.ZERO),
                    money(pos == null ? null : pos.getValor()), minimo, medio, maximo, abaixo,
                    idsAbertos.contains(p.getId()), vencidos.contains(p.getId()), proximos.contains(p.getId()));
        }).filter(d -> !somenteAbertos || d.possuiItensAbertos()).filter(d -> !somenteBaixo || d.abaixoDoMinimo())
                .sorted(Comparator.comparing(EstoqueDTO::produtoNome, String.CASE_INSENSITIVE_ORDER).thenComparing(EstoqueDTO::produtoId)).toList();
    }
    public record Pagina(List<EstoqueDTO> content, int number, int size, long totalElements, int totalPages, BigDecimal valorTotal) {}
    public Pagina pagina(String q, boolean abertos, boolean baixo, String situacao, int pagina, int tamanho) {
        validarSituacao(situacao);
        var pedido = org.springframework.data.domain.PageRequest.of(Math.max(0,pagina), Math.clamp(tamanho,1,100),
            org.springframework.data.domain.Sort.by("nome").ascending().and(org.springframework.data.domain.Sort.by("id")));
        var resultado = produtos.estoqueFiltrado(q.trim(),abertos,baixo,situacao,horario.hoje(),pedido);
        var ids = new HashSet<>(produtos.idsEstoque(q.trim(),abertos,baixo,situacao,horario.hoje()));
        var total = posicoes().values().stream().filter(p -> ids.contains(p.getProdutoId()))
            .map(p -> money(p.getValor())).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Pagina(adaptar(resultado.getContent(),false,false),pedido.getPageNumber(),pedido.getPageSize(),
            resultado.getTotalElements(),resultado.getTotalPages(),money(total));
    }
    public List<EstoqueDTO> filtrar(String q, boolean abertos, boolean baixo, String situacao) {
        validarSituacao(situacao);
        return adaptar(produtos.estoqueFiltrado(q.trim(),abertos,baixo,situacao,horario.hoje(),org.springframework.data.domain.Pageable.unpaged()).getContent(),false,false);
    }
    private void validarSituacao(String situacao) {
        if (!java.util.Set.of("TODOS","VENCIDO","PROXIMO").contains(situacao))
            throw new com.estoq.core.exceptions.FieldValidationException("situacao","Situação inválida.");
    }
}