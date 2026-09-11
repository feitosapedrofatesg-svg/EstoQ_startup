package com.estoq.business.lotes;

import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoteService {

    private final ILoteRepository repository;
    private final LoteAdapter adapter;

    public LoteModel encontrar(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Lote não encontrado.", HttpStatus.NOT_FOUND));
    }

    public LoteDTO buscar(Long id) {
        return adapter.toDto(encontrar(id));
    }

    public List<LoteDTO> listar(Long produtoId) {
        return repository.listarEstoque().stream()
                .filter(l -> produtoId == null || produtoId.equals(l.getProduto().getId()))
                .map(adapter::toDto).toList();
    }

    public List<LoteDTO> disponiveis(Long produtoId) {
        return repository.findDisponiveisBaixa(produtoId, LocalDate.now()).stream().map(adapter::toDto).toList();
    }

    public List<LoteDTO> vencidos() {
        return repository.listarEstoque().stream()
                .filter(l -> l.getQuantidadeAtual().signum() > 0 && l.estaVencido(LocalDate.now()))
                .map(adapter::toDto).toList();
    }

    public List<LoteDTO> proximosVencimento() {
        return repository.listarEstoque().stream().filter(this::proximoVencimento).map(adapter::toDto).toList();
    }

    public boolean proximoVencimento(LoteModel lote) {
        var par = lote.getProduto().getParametro();
        if (par == null || !par.isAtivo() || !lote.estaDisponivel(LocalDate.now())) {
            return false;
        }
        var dias = lote.diasParaVencimento(LocalDate.now());
        return dias != null && dias <= par.getDiasAlertaVencimento();
    }

    @Transactional
    public LoteModel criar(ProdutoModel produto, BigDecimal inicial, BigDecimal saldo, BigDecimal preco, LocalDate validade) {
        var lote = new LoteModel();
        lote.setProduto(produto);
        lote.setQuantidadeInicial(inicial);
        lote.setQuantidadeAtual(saldo);
        lote.setPrecoUnitario(preco);
        lote.setDataEntrada(LocalDate.now());
        lote.setDataValidade(validade);
        lote.setCodigo("PENDENTE-" + UUID.randomUUID());
        repository.saveAndFlush(lote);
        lote.setCodigo(String.format("L-%06d", lote.getId()));
        return lote;
    }
}