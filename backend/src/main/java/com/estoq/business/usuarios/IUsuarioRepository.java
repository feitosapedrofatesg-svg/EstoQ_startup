package com.estoq.business.usuarios;

import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;

public interface IUsuarioRepository extends IGenericRepository<UsuarioModel> {

    Optional<UsuarioModel> findByEmailIgnoreCaseAndAtivoTrue(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByPerfilAndAtivoTrue(Perfil perfil);
    long countByRestauranteId(Long restauranteId);
    Optional<UsuarioModel> findFirstByRestauranteIdAndPerfilAndAtivoTrueOrderByIdAsc(Long restauranteId, Perfil perfil);
}