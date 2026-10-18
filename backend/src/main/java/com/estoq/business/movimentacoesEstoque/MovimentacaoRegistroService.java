package com.estoq.business.movimentacoesEstoque;

import org.springframework.transaction.annotation.Propagation;

import com.estoq.business.sincronizacao.DadosAlteradosService;
import com.estoq.business.sincronizacao.GrupoDados;
import com.estoq.business.auditoria.AuditService;
import com.estoq.business.produtos.EstoqueService;
import com.estoq.business.desperdicios.DesperdicioClassificacao;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/** Persistência da trilha e resposta do estado já confirmado pelo flush otimista. */
@Service
@RequiredArgsConstructor
public class MovimentacaoRegistroService {

    private final DadosAlteradosService sincronizacao;
    private final IMovimentacaoEstoqueRepository repository;
    private final MovimentacaoAdapter adapter;
    private final EstoqueService estoque;
    private final AuditService auditoria;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Transactional
    public void salvar(MovimentacaoEstoqueModel mov) {
        repository.save(mov);
        sincronizacao.registrar(mov);
        auditoria.registrar(mov.getTipo().name(), "LOTE", mov.getLote().getId().toString(),
                mov.getQuantidade() + " de " + mov.getProduto().getNome(), mov.getUsuario());
    }

    @Transactional
    public MovimentacaoResultadoDTO concluir(Long produtoId, List<? extends MovimentacaoEstoqueModel> movs) {
        entityManager.flush();
        events.publishEvent(new EstoqueAlteradoEvent(produtoId));
        sincronizacao.registrarAtual(GrupoDados.ESTOQUE);
        return new MovimentacaoResultadoDTO(produtoId, estoque.obterSaldo(produtoId), movs.stream().map(adapter::toDto).toList());
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoDTO> consultar(Long produtoId, Long loteId, Long usuarioId, TipoMovimentacao tipo,
            LocalDateTime inicio, LocalDateTime fim) {
        var movimentos = new ArrayList<>(repository.consultar(produtoId, loteId, usuarioId, tipo, inicio, fim));
        if (tipo == TipoMovimentacao.DESPERDICIO) {
            repository.consultar(produtoId, loteId, usuarioId, TipoMovimentacao.AJUSTE, inicio, fim).stream()
                    .filter(DesperdicioClassificacao::perdaBalanco).forEach(movimentos::add);
        }
        return movimentos.stream()
                .sorted(Comparator.comparing(MovimentacaoEstoqueModel::getDataHora)
                        .thenComparing(MovimentacaoEstoqueModel::getId).reversed())
                .map(adapter::toDto).toList();
    }

    /** Um flush e um aviso por grupo, sem recalcular saldos para cada produto. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void concluirVarios(List<? extends MovimentacaoEstoqueModel> movs) {
        entityManager.flush();
        events.publishEvent(new EstoqueVariosAlteradosEvent(movs.stream().map(m -> m.getProduto().getId()).distinct().toList()));
        sincronizacao.registrarAtual(GrupoDados.ESTOQUE);
    }

    public List<MovimentacaoDTO> adaptar(List<? extends MovimentacaoEstoqueModel> movs) {
        return movs.stream().map(adapter::toDto).toList();
    }
}
