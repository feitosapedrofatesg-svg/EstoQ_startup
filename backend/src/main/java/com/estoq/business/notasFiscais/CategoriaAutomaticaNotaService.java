package com.estoq.business.notasFiscais;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.categorias.*;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.restaurantes.IRestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaAutomaticaNotaService {
    private final ICategoriaRepository repository;
    private final CategoriaService categorias;
    private final AssociadorCategoriaNota associador;
    private final IRestauranteRepository restaurantes;
    private final UsuarioAtual usuarioAtual;

    @Transactional
    public void prepararConfirmacao() {
        // A ordem dos bloqueios é a mesma antes de criar categorias, produtos e entradas.
        restaurantes.bloquearParaImportacao(usuarioAtual.obter().getRestauranteId()).orElseThrow();
    }

    @Transactional
    public Long resolver(String nomeProduto, List<ProdutoModel> catalogo) {
        var sugerida = associador.sugerir(nomeProduto, catalogo, repository.findAllByAtivoTrue());
        if (sugerida != null) return sugerida;
        // Serializa a criação por cozinha para duas notas não criarem a categoria duas vezes.
        restaurantes.bloquearParaImportacao(usuarioAtual.obter().getRestauranteId()).orElseThrow();
        var existente = repository.findAllByAtivoTrue().stream()
            .filter(c -> AssociadorProdutoNota.normalizar(c.getNome()).equals("SEM CLASSIFICACAO")).findFirst();
        if (existente.isPresent()) return existente.get().getId();
        var categoria = new CategoriaDTO();
        categoria.setNome("Sem classificação");
        categoria.setDescricao("Itens de notas fiscais sem uma categoria identificada automaticamente.");
        return categorias.criar(categoria).getId();
    }
}
