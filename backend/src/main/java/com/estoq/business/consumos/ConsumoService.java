package com.estoq.business.consumos;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsumoService {

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
                ? repository.findDisponiveisBaixa(d.produtoId(), LocalDate.now())
                : List.of(lotes.encontrar(d.loteId()));
        var usuario = usuarioAtual.obter();
        var restante = d.quantidade();
        var movs = new ArrayList<ConsumoModel>();
        for (var lote : candidatos) {
            if (restante.signum() == 0) {
                break;
            }
            if (!lote.getProduto().getId().equals(d.produtoId())) {
                throw new FieldValidationException("loteId", "O lote não pertence ao produto.");
            }
            if (!lote.estaDisponivel(LocalDate.now())) {
                throw new ConflictException("Lote vencido, sem validade ou indisponível para consumo.");
            }
            baixa.conferirVersao(lote, d.versionLote());
            var qtd = restante.min(lote.getQuantidadeAtual());
            for (var parcela : baixa.baixar(lote, qtd)) {
                var c = new ConsumoModel();
                c.registrar(lote, usuario, parcela.quantidade(), parcela.anterior(), parcela.posterior(), d.observacao(), parcela.aberto());
                registros.salvar(c);
                movs.add(c);
            }
            restante = restante.subtract(qtd);
        }
        if (restante.signum() > 0) {
            throw new ConflictException("Estoque disponível insuficiente para o consumo solicitado.");
        }
        return registros.concluir(d.produtoId(), movs);
    }
}