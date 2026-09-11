package com.estoq.core.repositories;

import com.estoq.core.domains.BaseModel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface IGenericRepository<E extends BaseModel> extends JpaRepository<E, Long> {

    Optional<E> findByIdAndAtivoTrue(Long id);
    Page<E> findAllByAtivoTrue(Pageable pageable);
    List<E> findAllByAtivoTrue();
    long countByAtivoTrue();
}