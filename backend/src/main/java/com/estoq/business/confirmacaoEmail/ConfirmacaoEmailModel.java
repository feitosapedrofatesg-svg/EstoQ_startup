package com.estoq.business.confirmacaoEmail;

import com.estoq.core.domains.BaseModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Token público de uso único. Nunca guarda o token original. */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "confirmacoes_email")
public class ConfirmacaoEmailModel extends BaseModel {
    @Column(nullable = false) private Long usuarioId;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private LocalDateTime expiraEm;
}
