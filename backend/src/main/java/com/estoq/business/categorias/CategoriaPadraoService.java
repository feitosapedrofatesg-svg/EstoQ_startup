package com.estoq.business.categorias;

import com.estoq.business.restaurantes.IRestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Prepara o catálogo inicial da loja, sem criar produtos nem movimentar o estoque. */
@Service
@RequiredArgsConstructor
public class CategoriaPadraoService {
    public static final List<String> NOMES = List.of(
        "Carnes", "Peixes e frutos do mar", "Laticínios", "Grãos", "Hortifruti", "Massas",
        "Temperos e condimentos", "Óleos e molhos", "Mercearia", "Bebidas", "Limpeza",
        "Descartáveis e embalagens", "Sem classificação"
    );
    private final ICategoriaRepository repository;
    private final IRestauranteRepository restaurantes;

    @Transactional
    public void inicializar(Long restauranteId) {
        restaurantes.bloquearParaImportacao(restauranteId).orElseThrow();
        // O cadastro público não tem usuário autenticado: a consulta precisa indicar a loja.
        var existentes = repository.findAllByRestauranteIdAndAtivoTrue(restauranteId).stream()
            .map(c -> c.getNome().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        for (var nome : NOMES) {
            if (existentes.contains(nome.toUpperCase(Locale.ROOT))) continue;
            var categoria = new CategoriaModel();
            categoria.setRestauranteId(restauranteId);
            categoria.setNome(nome);
            categoria.setDescricao("Categoria padrão do restaurante.");
            repository.save(categoria);
        }
        repository.flush();
    }
}
