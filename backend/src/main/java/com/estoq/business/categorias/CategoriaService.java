package com.estoq.business.categorias;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    public Page<CategoriaDTO> listar(Pageable pageable, String q) {
        if (q == null || q.isBlank()) return super.listar(pageable);
        return ((ICategoriaRepository) repository).findByAtivoTrueAndNomeContainingIgnoreCase(q.trim(), pageable).map(adapter::toDto);
    }

    @Override
    protected void beforeDelete(CategoriaModel c) {
        if (produtos.existsByCategoria_IdAndAtivoTrue(c.getId())) {
            throw new ConflictException("A categoria possui produtos ativos.");
        }
    }
}
