package com.estoq.business.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IAuditoriaRepository extends JpaRepository<AuditoriaModel, Long> {
}