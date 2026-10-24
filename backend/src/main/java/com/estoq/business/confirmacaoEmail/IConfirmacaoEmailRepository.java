package com.estoq.business.confirmacaoEmail;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface IConfirmacaoEmailRepository extends JpaRepository<ConfirmacaoEmailModel, Long> {
    Optional<ConfirmacaoEmailModel> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfirmacaoEmailModel c where c.tokenHash=:hash")
    Optional<ConfirmacaoEmailModel> bloquear(String hash);

    long countByUsuarioIdAndDataHoraCriacaoAfter(Long usuarioId, LocalDateTime inicio);

    @Modifying
    @Query("update ConfirmacaoEmailModel c set c.ativo=false, c.version=c.version+1 where c.usuarioId=:usuarioId and c.ativo=true")
    void invalidarAnteriores(Long usuarioId);

    long deleteByExpiraEmBefore(LocalDateTime limite);
}
