package com.estoq.business.ajustes;

import com.estoq.business.balancos.BalancoModel;
import com.estoq.business.itensBalanco.ItemBalancoModel;
import com.estoq.business.lotes.BaixaLoteService;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.lotes.LoteModel;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoRegistroService;
import com.estoq.business.usuarios.UsuarioModel;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Distribui a diferença apurada entre lotes em FIFO por validade (decisão B). */
@Service
@RequiredArgsConstructor
public class AjusteService {

    private final ILoteRepository lotes;
    private final LoteService loteService;
    private final BaixaLoteService baixa;
    private final MovimentacaoRegistroService registros;

    @Transactional
    public void aplicarItem(BalancoModel balanco, ItemBalancoModel item) {
        if (item.isAjusteAplicado()) {
            return;
        }
        var diferenca = item.getDiferenca();
        if (diferenca.signum() == 0) {
            item.setAjusteAplicado(true);
            return;
        }
        var usuario = balanco.getUsuario();
        if (diferenca.signum() < 0) {
            aplicarDeficit(balanco, item, diferenca.negate(), usuario);
        } else {
            aplicarSuperavit(balanco, item, diferenca, usuario);
        }
        item.setAjusteAplicado(true);
    }

    private void aplicarDeficit(BalancoModel balanco, ItemBalancoModel item, BigDecimal faltando, UsuarioModel usuario) {
        var produtoId = item.getProduto().getId();
        var observacao = "Ajuste de déficit do balanço #" + balanco.getId();
        var restante = faltando;
        var movs = new ArrayList<AjusteModel>();
        for (var lote : lotes.findTodosBaixa(produtoId)) {
            if (restante.signum() == 0) {
                break;
            }
            var qtd = restante.min(lote.getQuantidadeAtual());
            if (qtd.signum() == 0) {
                continue;
            }
            for (var parcela : baixa.baixar(lote, qtd)) {
                var a = new AjusteModel();
                a.setItemBalanco(item);
                a.registrar(lote, usuario, parcela.quantidade().negate(), parcela.anterior(), parcela.posterior(), observacao, parcela.aberto());
                registros.salvar(a);
                movs.add(a);
            }
            restante = restante.subtract(qtd);
        }
        if (!movs.isEmpty()) {
            registros.concluir(produtoId, movs);
        }
    }

    private void aplicarSuperavit(BalancoModel balanco, ItemBalancoModel item, BigDecimal sobra, UsuarioModel usuario) {
        var produto = item.getProduto();
        var candidatos = lotes.findDisponiveisBaixa(produto.getId(), LocalDate.now());
        LoteModel lote = candidatos.isEmpty() ? null : candidatos.getFirst();
        if (lote == null) {
            lote = loteService.criar(produto, sobra, sobra, BigDecimal.ZERO, null);
        } else {
            lote.somar(sobra);
        }
        var anterior = lote.getQuantidadeAtual().subtract(sobra);
        var a = new AjusteModel();
        a.setItemBalanco(item);
        a.registrar(lote, usuario, sobra, anterior, lote.getQuantidadeAtual(), "Ajuste de sobra do balanço #" + balanco.getId(), null);
        registros.salvar(a);
        registros.concluir(produto.getId(), List.of(a));
    }
}