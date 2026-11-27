package com.estoq.business.registro;
import com.estoq.business.usuarios.UsuarioModel;
import com.estoq.core.helpers.HorarioOperacao;
import com.estoq.core.exceptions.FieldValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class DocumentosService {
    public static final String TERMOS="2026-11-27-minuta-1";
    public static final String PRIVACIDADE="2026-10-25";
    private final IAceiteDocumentoRepository repository;
    private final HorarioOperacao horario;
    public void validar(Boolean termos,Boolean privacidade,String versaoTermos,String versaoPrivacidade) {
        if(!Boolean.TRUE.equals(termos))throw new FieldValidationException("aceitouTermos","Leia e aceite os Termos de Uso.");
        if(!Boolean.TRUE.equals(privacidade))throw new FieldValidationException("cientePrivacidade","Leia e confirme ciência da Política de Privacidade.");
        if(!TERMOS.equals(versaoTermos)||!PRIVACIDADE.equals(versaoPrivacidade))
            throw new FieldValidationException("versaoDocumentos","Os documentos mudaram. Recarregue e confira as versões atuais.");
    }
    public void registrar(UsuarioModel responsavel) {
        var agora=horario.agoraUtc();
        for(String documento:new String[]{"TERMOS","PRIVACIDADE"}) {
            var a=new AceiteDocumentoModel();a.setRestauranteId(responsavel.getRestauranteId());
            a.setResponsavelId(responsavel.getId());a.setResponsavelNome(responsavel.getNome());a.setDocumento(documento);
            a.setVersao(documento.equals("TERMOS")?TERMOS:PRIVACIDADE);a.setConfirmadoEm(agora);repository.save(a);
        }
    }
}
