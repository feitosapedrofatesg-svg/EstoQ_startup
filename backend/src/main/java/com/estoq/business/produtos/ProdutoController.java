package com.estoq.business.produtos;

import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.lotes.LoteService;
import com.estoq.core.controllers.GenericController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController extends GenericController<ProdutoDTO> {

    private final ProdutoService produtos;
    private final EstoqueService estoque;
    private final LoteService lotes;

    public ProdutoController(ProdutoService produtos, EstoqueService estoque, LoteService lotes) {
        super(produtos);
        this.produtos = produtos;
        this.estoque = estoque;
        this.lotes = lotes;
    }

    @GetMapping("/{id}/saldo")
    public BigDecimal saldo(@PathVariable Long id) {
        produtos.buscar(id);
        return estoque.obterSaldo(id);
    }

    @GetMapping("/{id}/lotes-disponiveis")
    public List<LoteDTO> disponiveis(@PathVariable Long id) {
        produtos.buscar(id);
        return lotes.disponiveis(id);
    }

    @GetMapping("/estoque-baixo")
    public List<EstoqueDTO> baixo() {
        return estoque.listar(false, true);
    }
}