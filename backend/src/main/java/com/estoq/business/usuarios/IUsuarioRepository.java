package com.estoq.business.usuarios;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;

import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;

public interface IUsuarioRepository extends IGenericRepository<UsuarioModel> {

    @Query("select u from UsuarioModel u where lower(u.nome) like lower(concat('%',:q,'%')) or lower(u.email) like lower(concat('%',:q,'%'))")
    Page<UsuarioModel> pesquisar(String q, Pageable pageable);

    Optional<UsuarioModel> findByEmailIgnoreCaseAndAtivoTrue(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByPerfilAndAtivoTrue(Perfil perfil);
    long countByRestauranteId(Long restauranteId);
    Optional<UsuarioModel> findFirstByRestauranteIdAndPerfilAndAtivoTrueOrderByIdAsc(Long restauranteId, Perfil perfil);
}
