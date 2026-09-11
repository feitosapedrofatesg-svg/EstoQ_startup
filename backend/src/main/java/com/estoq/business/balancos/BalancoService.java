package com.estoq.business.balancos;

import com.estoq.business.ajustes.AjusteService;
import com.estoq.business.alertas.AlertaService;
import com.estoq.business.alertas.TipoAlerta;
import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.itensBalanco.ItemBalancoModel;
import com.estoq.business.produtos.EstoqueService;
import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalancoService {

    private final IBalancoRepository repository;
    private final IProdutoRepository produtos;
    private final EstoqueService estoque;
    private final IConfiguracaoBalancoRepository configuracoes;
    private final BalancoAdapter adapter;
    private final UsuarioAtual usuarioAtual;
    private final AjusteService ajustes;
    private final AlertaService alertas;

    public List<BalancoDTO> listar() {
        return repository.findAllByAtivoTrueOrderByDataHoraDesc().stream().map(adapter::toDto).toList();
    }

    public BalancoDTO buscar(Long id) {
        return adapter.toDto(encontrar(id));
    }

    private BalancoModel encontrar(Long id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new BusinessException("Balanço não encontrado.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public BalancoDTO criar(CriarBalancoRequestDTO dto) {
        var balanco = new BalancoModel();
        balanco.setDataHora(LocalDateTime.now());
        balanco.setTipo(dto.tipo() == null ? TipoBalanco.GERAL : dto.tipo());
        balanco.setStatus(StatusBalanco.PENDENTE);
        balanco.setUsuario(usuarioAtual.obter());
        repository.saveAndFlush(balanco);
        configuracoes.findAllByAtivoTrueOrderByIdAsc().stream().findFirst().ifPresent(alertas::reavaliarBalancoPendente);
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO iniciar(Long id) {
        var balanco = encontrar(id);
        if (!balanco.getItens().isEmpty()) {
            throw new ConflictException("O balanço já foi iniciado.");
        }
        if (balanco.getStatus() != StatusBalanco.PENDENTE) {
            throw new ConflictException("Somente um balanço PENDENTE pode ser iniciado.");
        }
        for (var produto : produtos.findAllByAtivoTrue()) {
            var item = new ItemBalancoModel();
            item.setBalanco(balanco);
            item.setProduto(produto);
            item.setQuantidadeSistema(estoque.obterSaldo(produto.getId()));
            item.setAjusteAplicado(false);
            balanco.getItens().add(item);
        }
        balanco.setStatus(StatusBalanco.EM_ANDAMENTO);
        repository.flush();
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO registrarContagem(Long id, Long itemId, ContagemRequestDTO dto) {
        var balanco = encontrar(id);
        if (balanco.getStatus() != StatusBalanco.EM_ANDAMENTO) {
            throw new ConflictException("Registre contagens apenas em balanço EM_ANDAMENTO.");
        }
        var item = balanco.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BusinessException("Item não pertence a este balanço.", HttpStatus.NOT_FOUND));
        if (dto.quantidadeFisica() == null || dto.quantidadeFisica().signum() < 0) {
            throw new FieldValidationException("quantidadeFisica", "Informe a quantidade física contada (não negativa).");
        }
        item.definirContagem(dto.quantidadeFisica());
        repository.flush();
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO confirmar(Long id) {
        var balanco = encontrar(id);
        if (balanco.getStatus() != StatusBalanco.EM_ANDAMENTO) {
            throw new ConflictException("Confirme apenas um balanço EM_ANDAMENTO.");
        }
        if (balanco.getItens().isEmpty()) {
            throw new ConflictException("Não confirme um balanço sem itens.");
        }
        for (var item : balanco.getItens()) {
            if (item.getQuantidadeFisica() == null) {
                throw new ConflictException("Confirme todos os itens antes de encerrar o balanço.");
            }
        }
        balanco.setStatus(StatusBalanco.CONCLUIDO);
        alertas.reavaliarDiferenca(balanco);
        alertas.apagarAbertos(TipoAlerta.BALANCO_PENDENTE, null, null);
        repository.flush();
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO gerarAjustes(Long id) {
        var balanco = encontrar(id);
        if (balanco.getStatus() != StatusBalanco.CONCLUIDO) {
            throw new ConflictException("Gere ajustes apenas em um balanço CONCLUIDO.");
        }
        for (var item : balanco.getItens()) {
            ajustes.aplicarItem(balanco, item);
            alertas.apagarAbertos(TipoAlerta.DIFERENCA_ESTOQUE, item.getProduto().getId(), null);
        }
        repository.flush();
        return adapter.toDto(balanco);
    }
}