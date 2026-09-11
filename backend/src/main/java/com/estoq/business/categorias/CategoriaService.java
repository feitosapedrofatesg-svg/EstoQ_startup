package com.estoq.business.categorias;

import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.services.GenericService;

import org.springframework.stereotype.Service;

@Service
public class CategoriaService extends GenericService<CategoriaModel, CategoriaDTO> {

    private final IProdutoRepository produtos;

    public CategoriaService(ICategoriaRepository r, CategoriaAdapter a, ICategoriaValidation v, IProdutoRepository produtos) {
        super(r, a, v);
        this.produtos = produtos;
    }

    @Override
    protected void beforeDelete(CategoriaModel c) {
        if (produtos.existsByCategoria_IdAndAtivoTrue(c.getId())) {
            throw new ConflictException("A categoria possui produtos ativos.");
        }
    }
}