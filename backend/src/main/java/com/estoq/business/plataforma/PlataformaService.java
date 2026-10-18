package com.estoq.business.plataforma;

import com.estoq.business.auditoria.AuditoriaPlataformaModel;
import com.estoq.business.auditoria.IAuditoriaPlataformaRepository;
import com.estoq.business.auth.UsuarioAtual;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.usuarios.IUsuarioRepository;
import com.estoq.business.usuarios.Perfil;
import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Painel exclusivo do perfil PLATAFORMA: enxerga todos os restaurantes,
 * suspende/ativa e recupera a senha do administrador de cada um.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlataformaService {

    private final IRestauranteRepository restaurantes;
    private final IUsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final UsuarioAtual usuarioAtual;
    private final IAuditoriaPlataformaRepository auditoria;

    public List<RestauranteDTO> listar() {
        var contagens = new java.util.HashMap<Long, Long>();
        usuarios.contarPorRestaurante().forEach(c -> contagens.put(c.getRestauranteId(), c.getQuantidade()));
        return restaurantes.findAll(Sort.by("id")).stream()
                .map(r -> new RestauranteDTO(r.getId(), r.getNome(), r.isAtivo(), r.getDataHoraCriacao(),
                        contagens.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional
    public void atualizarStatus(Long id, boolean ativo) {
        var restaurante = restaurantes.bloquearAdministracao(id)
                .orElseThrow(() -> new BusinessException("Restaurante não encontrado.", HttpStatus.NOT_FOUND));
        restaurante.setAtivo(ativo);
        restaurantes.flush();
        auditar(id, ativo ? "RESTAURANTE_REATIVADO" : "RESTAURANTE_SUSPENSO", null);
    }

    @Transactional
    public void redefinirSenhaAdmin(Long id, String senha) {
        restaurantes.bloquearAdministracao(id)
                .orElseThrow(() -> new BusinessException("Restaurante não encontrado.", HttpStatus.NOT_FOUND));
        var admin = usuarios.findFirstByRestauranteIdAndPerfilAndAtivoTrueOrderByIdAsc(id, Perfil.ADMIN)
                .orElseThrow(() -> new BusinessException("Restaurante sem administrador ativo.", HttpStatus.NOT_FOUND));
        admin.setSenha(encoder.encode(senha));
        usuarios.flush();
        auditar(id, "SENHA_ADMIN_REDEFINIDA", admin.getId());
    }

    private void auditar(Long restauranteId, String acao, Long usuarioAlvoId) {
        var registro = new AuditoriaPlataformaModel();
        registro.setUsuarioId(usuarioAtual.obter().getId());
        registro.setRestauranteId(restauranteId);
        registro.setAcao(acao);
        registro.setUsuarioAlvoId(usuarioAlvoId);
        auditoria.save(registro);
    }
}
