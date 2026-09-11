package com.estoq.business.desperdicios;

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

@Service
@RequiredArgsConstructor
public class DesperdicioService {

    private final ProdutoService produtos;
    private final LoteService lotes;
    private final ILoteRepository repository;
    private final BaixaLoteService baixa;
    private final DesperdicioValidation validation;
    private final UsuarioAtual usuarioAtual;
    private final MovimentacaoRegistroService registros;

    @Transactional
    public MovimentacaoResultadoDTO registrarDesperdicio(DesperdicioRequestDTO d) {
        produtos.encontrar(d.produtoId());
        baixa.validarQuantidade(d.quantidade());
        validation.validar(d.motivo(), d.descricaoMotivo());
        List<LoteModel> candidatos = d.loteId() == null
                ? repository.findTodosBaixa(d.produtoId())
                : List.of(lotes.encontrar(d.loteId()));
        var usuario = usuarioAtual.obter();
        var restante = d.quantidade();
        var movs = new ArrayList<DesperdicioModel>();
        for (var lote : candidatos) {
            if (restante.signum() == 0) {
                break;
            }
            if (!lote.getProduto().getId().equals(d.produtoId())) {
                throw new FieldValidationException("loteId", "O lote não pertence ao produto.");
            }
            baixa.conferirVersao(lote, d.versionLote());
            var qtd = restante.min(lote.getQuantidadeAtual());
            if (qtd.signum() == 0) {
                continue;
            }
            for (var parcela : baixa.baixar(lote, qtd)) {
                var m = new DesperdicioModel();
                m.setMotivo(d.motivo());
                m.setDescricaoMotivo(d.descricaoMotivo());
                m.registrar(lote, usuario, parcela.quantidade(), parcela.anterior(), parcela.posterior(), d.observacao(), parcela.aberto());
                registros.salvar(m);
                movs.add(m);
            }
            restante = restante.subtract(qtd);
        }
        if (restante.signum() > 0) {
            throw new ConflictException("Desperdício maior que o estoque existente.");
        }
        return registros.concluir(d.produtoId(), movs);
    }
}