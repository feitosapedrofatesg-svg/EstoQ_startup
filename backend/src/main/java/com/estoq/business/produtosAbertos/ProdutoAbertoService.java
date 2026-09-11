package com.estoq.business.produtosAbertos;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.consumos.ConsumoModel;
import com.estoq.business.desperdicios.DesperdicioModel;
import com.estoq.business.desperdicios.MotivoDesperdicio;
import com.estoq.business.lotes.BaixaLoteService;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoRegistroService;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdutoAbertoService {

    private final IProdutoAbertoRepository repository;
    private final ProdutoAbertoAdapter adapter;
    private final ProdutoAbertoValidation validation;
    private final LoteService lotes;
    private final BaixaLoteService baixa;
    private final UsuarioAtual usuarioAtual;
    private final MovimentacaoRegistroService registros;
    private final EntityManager entityManager;

    public List<ProdutoAbertoDTO> listar(Long produtoId, Boolean finalizado) {
        return repository.listar(produtoId, finalizado).stream().map(adapter::toDto).toList();
    }

    public ProdutoAbertoDTO buscar(Long id) {
        return adapter.toDto(encontrar(id));
    }

    private ProdutoAbertoModel encontrar(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Item aberto não encontrado.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ProdutoAbertoDTO abrirEmbalagem(AbrirProdutoRequestDTO d) {
        var lote = lotes.encontrar(d.loteId());
        if (!lote.getProduto().getId().equals(d.produtoId())) {
            throw new FieldValidationException("loteId", "O lote não pertence ao produto.");
        }
        if (!lote.estaDisponivel(LocalDate.now())) {
            throw new ConflictException("Lote vencido, sem validade ou indisponível.");
        }
        baixa.conferirVersao(lote, d.versionLote());
        validation.validarAbertura(d.quantidadeDaEmbalagem(), d.quantoUsouAgora(),
                lote.getQuantidadeAtual().subtract(repository.restanteNoLote(lote.getId())));
        // Mesmo sem consumo, duas aberturas não podem reservar a mesma parcela do lote.
        entityManager.lock(lote, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        var a = new ProdutoAbertoModel();
        a.setLote(lote);
        a.setUsuario(usuarioAtual.obter());
        a.setDataAbertura(LocalDateTime.now());
        a.setQuantidadeAberta(d.quantidadeDaEmbalagem());
        a.utilizar(d.quantoUsouAgora());
        repository.save(a);
        if (d.quantoUsouAgora().signum() > 0) {
            var anterior = lote.getQuantidadeAtual();
            lote.baixar(d.quantoUsouAgora());
            var c = new ConsumoModel();
            c.registrar(lote, a.getUsuario(), d.quantoUsouAgora(), anterior, lote.getQuantidadeAtual(), d.observacao(), a);
            registros.salvar(c);
        }
        registros.concluir(d.produtoId(), List.of());
        return adapter.toDto(a);
    }

    @Transactional
    public ProdutoAbertoDTO consumirProdutoAberto(Long id, ConsumirProdutoAbertoRequestDTO d) {
        var a = prepararBaixa(id, d.quantidade(), d.version(), d.versionLote());
        var lote = a.getLote();
        if (!lote.estaDisponivel(LocalDate.now())) {
            throw new ConflictException("O lote do item aberto está vencido, sem validade ou indisponível.");
        }
        var anterior = lote.getQuantidadeAtual();
        lote.baixar(d.quantidade());
        a.utilizar(d.quantidade());
        var c = new ConsumoModel();
        c.registrar(lote, usuarioAtual.obter(), d.quantidade(), anterior, lote.getQuantidadeAtual(), d.observacao(), a);
        registros.salvar(c);
        registros.concluir(lote.getProduto().getId(), List.of(c));
        return adapter.toDto(a);
    }

    @Transactional
    public ProdutoAbertoDTO desperdicarProdutoAberto(Long id, DesperdicarProdutoAbertoRequestDTO d) {
        var a = prepararBaixa(id, d.quantidade(), d.version(), d.versionLote());
        var lote = a.getLote();
        var anterior = lote.getQuantidadeAtual();
        lote.baixar(d.quantidade());
        a.utilizar(d.quantidade());
        var m = new DesperdicioModel();
        m.setMotivo(MotivoDesperdicio.SOBRA_NAO_APROVEITADA);
        m.registrar(lote, usuarioAtual.obter(), d.quantidade(), anterior, lote.getQuantidadeAtual(), d.observacao(), a);
        registros.salvar(m);
        registros.concluir(lote.getProduto().getId(), List.of(m));
        return adapter.toDto(a);
    }

    private ProdutoAbertoModel prepararBaixa(Long id, BigDecimal qtd, Long version, Long versionLote) {
        var a = encontrar(id);
        baixa.validarQuantidade(qtd);
        baixa.conferirVersao(a.getLote(), versionLote);
        if (version != null && !Objects.equals(version, a.getVersion())) {
            throw new ConflictException();
        }
        if (!a.getLote().isAtivo() || !a.getLote().getProduto().isAtivo()) {
            throw new ConflictException("Produto ou lote inativo.");
        }
        if (a.isFinalizado() || qtd.compareTo(a.getQuantidadeRestante()) > 0) {
            throw new ConflictException("Item finalizado ou quantidade maior que o restante.");
        }
        return a;
    }
}