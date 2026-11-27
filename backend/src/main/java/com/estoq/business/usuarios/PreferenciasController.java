package com.estoq.business.usuarios;
import com.estoq.business.auth.UsuarioAtual;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
@RestController @RequiredArgsConstructor @RequestMapping("/api/auth/preferencias")
public class PreferenciasController {
    private final UsuarioAtual atual;
    private final IUsuarioRepository usuarios;
    @GetMapping @Transactional(readOnly=true)
    public PreferenciasDTO consultar(){var u=atual.obter();return new PreferenciasDTO(u.getTema(),u.getFonte(),u.getIdioma());}
    @PutMapping @Transactional
    public PreferenciasDTO salvar(@Valid @RequestBody PreferenciasDTO p){
        var u=atual.obter();u.setTema(p.tema());u.setFonte(p.fonte());u.setIdioma(p.idioma());usuarios.saveAndFlush(u);return p;
    }
}
