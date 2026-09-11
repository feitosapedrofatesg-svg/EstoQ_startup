package com.estoq.business.produtos;

import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.parametrosEstoque.IParametroEstoqueRepository;
import com.estoq.business.parametrosEstoque.ParametroEstoqueModel;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.services.GenericService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class ProdutoService extends GenericService<ProdutoModel, ProdutoDTO> {

    private final ICategoriaRepository categorias;
    private final IParametroEstoqueRepository parametros;
    private final ILoteRepository lotes;
    private final EstoqueService estoque;

    public ProdutoService(IProdutoRepository r, ProdutoAdapter a, IProdutoValidation v, ICategoriaRepository categorias,
            IParametroEstoqueRepository parametros, ILoteRepository lotes, EstoqueService estoque) {
        super(r, a, v);
        this.categorias = categorias;
        this.parametros = parametros;
        this.lotes = lotes;
        this.estoque = estoque;
    }

    public ProdutoModel encontrar(Long id) {
        return findActive(id);
    }

    @Override
    public Page<ProdutoDTO> listar(Pageable pageable) {
        var saldos = estoque.posicoes();
        return repository.findAllByAtivoTrue(pageable).map(p -> ((ProdutoAdapter) adapter).toDto(p,
                saldos.containsKey(p.getId()) ? saldos.get(p.getId()).getSaldo() : BigDecimal.ZERO));
    }

    @Override
    @Transactional
    public ProdutoDTO atualizar(Long id, ProdutoDTO dto) {
        var p = findActive(id);
        if (p.getUnidadeMedida() != dto.getUnidadeMedida() && lotes.existsByProduto_Id(id)) {
            throw new ConflictException("Não altere a unidade de um produto com histórico de lotes.");
        }
        return super.atualizar(id, dto);
    }

    @Override
    protected void beforeSave(ProdutoModel p, ProdutoDTO dto) {
        p.setCategoria(categorias.findByIdAndAtivoTrue(dto.getCategoriaId())
                .orElseThrow(() -> new BusinessException("Categoria não encontrada ou inativa.", HttpStatus.NOT_FOUND)));
    }

    @Override
    protected void afterSave(ProdutoModel p) {
        if (p.getParametro() == null) {
            var par = new ParametroEstoqueModel();
            par.setProduto(p);
            par.setDataAtualizacao(LocalDateTime.now());
            parametros.saveAndFlush(par);
            p.setParametro(par);
        }
    }

    @Override
    protected void beforeDelete(ProdutoModel p) {
        if (estoque.obterSaldo(p.getId()).signum() != 0) {
            throw new ConflictException("Zere o estoque por movimentações antes de desativar o produto.");
        }
    }
}