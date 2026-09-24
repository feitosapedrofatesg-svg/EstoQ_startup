package com.estoq.business.plataforma;

import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.business.restaurantes.RestauranteModel;
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

    public List<RestauranteDTO> listar() {
        return restaurantes.findAll(Sort.by("id")).stream()
                .map(r -> new RestauranteDTO(r.getId(), r.getNome(), r.isAtivo(), r.getDataHoraCriacao(),
                        usuarios.countByRestauranteId(r.getId())))
                .toList();
    }

    @Transactional
    public void atualizarStatus(Long id, boolean ativo) {
        var restaurante = encontrar(id);
        restaurante.setAtivo(ativo);
        restaurantes.flush();
    }

    @Transactional
    public void redefinirSenhaAdmin(Long id, String senha) {
        encontrar(id);
        var admin = usuarios.findFirstByRestauranteIdAndPerfilAndAtivoTrueOrderByIdAsc(id, Perfil.ADMIN)
                .orElseThrow(() -> new BusinessException("Restaurante sem administrador ativo.", HttpStatus.NOT_FOUND));
        admin.setSenha(encoder.encode(senha));
        usuarios.flush();
    }

    private RestauranteModel encontrar(Long id) {
        return restaurantes.findById(id)
                .orElseThrow(() -> new BusinessException("Restaurante não encontrado.", HttpStatus.NOT_FOUND));
    }
}