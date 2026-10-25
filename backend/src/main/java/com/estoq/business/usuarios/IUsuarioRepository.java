package com.estoq.business.usuarios;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.estoq.core.repositories.IGenericRepository;

import java.util.Optional;

public interface IUsuarioRepository extends IGenericRepository<UsuarioModel> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioModel u where u.id=:id")
    Optional<UsuarioModel> bloquearPorId(Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioModel u where lower(u.email)=lower(:email) and u.ativo=true")
    Optional<UsuarioModel> bloquearPorEmail(String email);


    @Query("select u from UsuarioModel u where lower(u.nome) like lower(concat('%',:q,'%')) or lower(u.email) like lower(concat('%',:q,'%'))")
    Page<UsuarioModel> pesquisar(String q, Pageable pageable);

    Optional<UsuarioModel> findByEmailIgnoreCaseAndAtivoTrue(String email);
    Optional<UsuarioModel> findByEmailIgnoreCase(String email);
    Optional<UsuarioModel> findByGoogleSub(String googleSub);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    long countByPerfilAndAtivoTrue(Perfil perfil);
    @Query("select u.restauranteId as restauranteId, count(u) as quantidade from UsuarioModel u group by u.restauranteId")
    java.util.List<ContagemRestaurante> contarPorRestaurante();

    interface ContagemRestaurante {
        Long getRestauranteId();
        long getQuantidade();
    }

    long countByRestauranteId(Long restauranteId);
    Optional<UsuarioModel> findFirstByRestauranteIdAndPerfilAndAtivoTrueOrderByIdAsc(Long restauranteId, Perfil perfil);
}
