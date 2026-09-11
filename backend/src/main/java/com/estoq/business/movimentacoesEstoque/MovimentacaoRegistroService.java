package com.estoq.business.movimentacoesEstoque;

import com.estoq.business.auditoria.AuditService;
import com.estoq.business.produtos.EstoqueService;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Persistência da trilha e resposta do estado já confirmado pelo flush otimista. */
@Service
@RequiredArgsConstructor
public class MovimentacaoRegistroService {

    private final IMovimentacaoEstoqueRepository repository;
    private final MovimentacaoAdapter adapter;
    private final EstoqueService estoque;
    private final AuditService auditoria;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Transactional
    public void salvar(MovimentacaoEstoqueModel mov) {
        repository.save(mov);
        auditoria.registrar(mov.getTipo().name(), "LOTE", mov.getLote().getId().toString(),
                mov.getQuantidade() + " de " + mov.getProduto().getNome(), mov.getUsuario());
    }

    @Transactional
    public MovimentacaoResultadoDTO concluir(Long produtoId, List<? extends MovimentacaoEstoqueModel> movs) {
        entityManager.flush();
        events.publishEvent(new EstoqueAlteradoEvent(produtoId));
        return new MovimentacaoResultadoDTO(produtoId, estoque.obterSaldo(produtoId), movs.stream().map(adapter::toDto).toList());
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoDTO> consultar(Long produtoId, Long loteId, Long usuarioId, TipoMovimentacao tipo,
            LocalDateTime inicio, LocalDateTime fim) {
        return repository.consultar(produtoId, loteId, usuarioId, tipo, inicio, fim).stream().map(adapter::toDto).toList();
    }
}