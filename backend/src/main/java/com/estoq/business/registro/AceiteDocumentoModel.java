package com.estoq.business.registro;
import com.estoq.core.domains.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="aceites_documentos") @Getter @Setter @NoArgsConstructor
public class AceiteDocumentoModel extends TenantEntity {
    @Column(nullable=false) private Long responsavelId;
    @Column(nullable=false,length=120) private String responsavelNome;
    @Column(nullable=false,length=30) private String documento;
    @Column(nullable=false,length=60) private String versao;
    @Column(nullable=false) private LocalDateTime confirmadoEm;
}
