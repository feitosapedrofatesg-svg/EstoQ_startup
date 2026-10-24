package com.estoq.business.consumos;

import com.estoq.business.produtosAbertos.ProdutoAbertoModel;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.lotes.*;
import com.estoq.business.produtos.*;
import com.estoq.business.produtosAbertos.IProdutoAbertoRepository;
import com.estoq.business.movimentacoesEstoque.MovimentacaoRegistroService;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.core.exceptions.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsumoLoteService {

    private final HorarioOperacao horario;
    private final UsuarioAtual usuarios;
    private final IRestauranteRepository restaurantes;
    private final IOperacaoConsumoRepository operacoes;
    private final IProdutoRepository produtos;
    private final ILoteRepository lotes;
    private final IProdutoAbertoRepository abertos;
    private final ConsumoService consumos;
    private final MovimentacaoRegistroService registros;
    private final EstoqueService estoque;
    private final ObjectMapper json;

    @Transactional
    public ConsumoLoteResultadoDTO registrar(ConsumoLoteRequestDTO pedido) {
        var usuario = usuarios.obter();
        // O bloqueio é local ao restaurante. Duas confirmações da mesma chave aguardam
        // a primeira transação e recuperam sua resposta, inclusive após reinício do servidor.
        restaurantes.bloquearParaImportacao(usuario.getRestauranteId()).orElseThrow(ConflictException::new);
        var ordenados = pedido.itens().stream().sorted(Comparator.comparing(ConsumoRequestDTO::produtoId)).toList();
        String conteudo = escrever(ordenados);
        var anterior = operacoes.findByChave(pedido.chave().toString());
        if (anterior.isPresent()) {
            if (!anterior.get().getPedido().equals(conteudo))
                throw new ConflictException("Esta confirmação já foi usada com outros itens. Reabra a conferência.");
            try { return json.readValue(anterior.get().getResposta(), ConsumoLoteResultadoDTO.class); }
            catch (JacksonException e) { throw new IllegalStateException("Resposta de consumo inválida.", e); }
        }
        var ids = ordenados.stream().map(ConsumoRequestDTO::produtoId).toList();
        if (new HashSet<>(ids).size() != ids.size())
            throw new FieldValidationException("itens", "Cada produto deve aparecer uma única vez.");
        var catalogo = produtos.findAllByAtivoTrueAndIdIn(ids).stream()
                .collect(Collectors.toMap(ProdutoModel::getId, Function.identity()));
        var candidatos = lotes.findParaConsumoEmLote(ids);
        var embalagens = candidatos.isEmpty() ? List.<ProdutoAbertoModel>of()
                : abertos.findParaBaixaEmLote(candidatos.stream().map(LoteModel::getId).toList());
        var porLote = embalagens.stream().collect(Collectors.groupingBy(a -> a.getLote().getId()));
        var porProduto = candidatos.stream().collect(Collectors.groupingBy(l -> l.getProduto().getId()));
        var movimentos = new ArrayList<ConsumoModel>();
        for (var item : ordenados) {
            var produto = catalogo.get(item.produtoId());
            if (produto == null) throw new FieldValidationException("itens", "Produto indisponível: " + item.produtoId());
            var disponiveis = porProduto.getOrDefault(item.produtoId(), List.of()).stream()
                    .filter(l -> item.loteId() == null ? l.estaDisponivel(horario.hoje()) : l.getId().equals(item.loteId())).toList();
            try {
                movimentos.addAll(consumos.baixarConsumo(item, disponiveis, usuario, porLote));
            } catch (BaseException e) {
                // Uma falha interrompe toda a transação, sem deixar consumo parcial.
                throw new ConflictException(produto.getNome() + ": " + e.getMessage());
            }
        }
        var resultado = new ConsumoLoteResultadoDTO(estoqueConfirmado(movimentos), registros.adaptar(movimentos));
        var operacao = new OperacaoConsumoModel();
        operacao.setChave(pedido.chave().toString()); operacao.setPedido(conteudo); operacao.setResposta(escrever(resultado));
        operacoes.save(operacao);
        return resultado;
    }

    private List<EstoqueDTO> estoqueConfirmado(List<ConsumoModel> movimentos) {
        registros.concluirVarios(movimentos);
        return estoque.listar(false, false);
    }

    private String escrever(Object valor) {
        try { return json.writeValueAsString(valor); }
        catch (JacksonException e) { throw new IllegalStateException("Não foi possível preparar a confirmação.", e); }
    }
}
