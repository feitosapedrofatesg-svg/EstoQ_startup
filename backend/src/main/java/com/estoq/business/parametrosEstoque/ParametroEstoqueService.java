package com.estoq.business.parametrosEstoque;

import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.services.GenericService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ParametroEstoqueService extends GenericService<ParametroEstoqueModel, ParametroEstoqueDTO> {

    private final IProdutoRepository produtos;
    private final IParametroEstoqueRepository parametros;

    public ParametroEstoqueService(IParametroEstoqueRepository r, ParametroEstoqueAdapter a,
            IParametroEstoqueValidation v, IProdutoRepository produtos) {
        super(r, a, v);
        this.produtos = produtos;
        this.parametros = r;
    }

    @Override
    protected void beforeSave(ParametroEstoqueModel p, ParametroEstoqueDTO d) {
        if (p.getProduto() != null && !p.getProduto().getId().equals(d.getProdutoId())) {
            throw new ConflictException("Não é permitido transferir parâmetros entre produtos.");
        }
        boolean existe = p.getId() == null ? parametros.existsByProduto_Id(d.getProdutoId())
                : parametros.existsByProduto_IdAndIdNot(d.getProdutoId(), p.getId());
        if (existe) {
            throw new ConflictException("Este produto já possui parâmetros. Atualize o registro existente.");
        }
        p.setProduto(produtos.findByIdAndAtivoTrue(d.getProdutoId())
                .orElseThrow(() -> new BusinessException("Produto não encontrado.", HttpStatus.NOT_FOUND)));
    }

    @Override
    protected void beforeDelete(ParametroEstoqueModel p) {
        throw new ConflictException("O produto precisa de parâmetros de estoque. Edite os valores em vez de excluir.");
    }
}