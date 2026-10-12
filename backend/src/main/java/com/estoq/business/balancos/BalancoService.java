package com.estoq.business.balancos;

import com.estoq.business.sincronizacao.DadosAlteradosService;

import com.estoq.business.ajustes.AjusteService;
import com.estoq.business.alertas.AlertaService;
import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.categorias.ICategoriaRepository;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.itensBalanco.IItemBalancoRepository;
import com.estoq.core.exceptions.BusinessException;
import com.estoq.core.exceptions.ConflictException;
import com.estoq.core.exceptions.FieldValidationException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalancoService {

    private final DadosAlteradosService sincronizacao;
    private final jakarta.persistence.EntityManager entityManager;
    private final IBalancoRepository repository;
    private final IItemBalancoRepository itensBalanco;
    private final ICategoriaRepository categorias;
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
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Balanço não encontrado.", HttpStatus.NOT_FOUND));
    }

    private BalancoModel bloquear(Long id) {
        return repository.bloquear(id)
                .orElseThrow(() -> new BusinessException("Balanço não encontrado.", HttpStatus.NOT_FOUND));
    }

    private void conferirVersao(Long consultada, Long atual) {
        if (consultada == null) throw new FieldValidationException("version", "Recarregue a tela antes de continuar.");
        if (!Objects.equals(consultada, atual)) throw new ConflictException("O balanço ou a contagem mudou em outra sessão. Confira os dados atualizados.");
    }

    @Transactional
    public BalancoDTO criar(CriarBalancoRequestDTO dto) {
        var balanco = new BalancoModel();
        balanco.setDataHora(LocalDateTime.now(ZoneOffset.UTC));
        balanco.setTipo(dto.tipo() == null ? TipoBalanco.GERAL : dto.tipo());
        balanco.setStatus(StatusBalanco.PENDENTE);
        balanco.setUsuario(usuarioAtual.obter());
        if (balanco.getTipo() == TipoBalanco.PARCIAL) {
            if (dto.categorias() == null || dto.categorias().isEmpty()) {
                throw new FieldValidationException("categorias", "Balanço parcial exige ao menos uma categoria.");
            }
            var selecionadas = categorias.findAllById(dto.categorias());
            balanco.getCategorias().addAll(selecionadas);
        }
        repository.saveAndFlush(balanco);
        configuracoes.findAllByAtivoTrueOrderByIdAsc().stream().findFirst()
                .ifPresent(alertas::reavaliarBalancoPendente);
        sincronizacao.registrar(balanco);
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO iniciar(Long id, VersaoBalancoRequestDTO dto) {
        var balanco = bloquear(id);
        conferirVersao(dto.version(), balanco.getVersion());
        if (itensBalanco.existsByBalanco_Id(id)) {
            throw new ConflictException("O balanço já foi iniciado.");
        }
        if (balanco.getStatus() != StatusBalanco.PENDENTE) {
            throw new ConflictException("Somente um balanço PENDENTE pode ser iniciado.");
        }
        if (balanco.getTipo() == TipoBalanco.PARCIAL && balanco.getCategorias().isEmpty()) {
            throw new ConflictException("Balanço parcial sem categorias selecionadas.");
        }
        balanco.setStatus(StatusBalanco.EM_ANDAMENTO);
        repository.flush();
        itensBalanco.criarRetrato(id, balanco.getRestauranteId());
        // A inserção em lote não passa pela coleção gerenciada; descarte seu retrato vazio.
        entityManager.refresh(balanco);
        balanco = encontrar(id);
        sincronizacao.registrar(balanco);
        return adapter.toDto(balanco);
    }

    @Transactional
    public ItemBalancoDTO registrarContagem(Long id, Long itemId, ContagemRequestDTO dto) {
        var balanco = bloquear(id);
        var item = itensBalanco.findByIdAndBalanco_IdAndAtivoTrue(itemId, id)
                .orElseThrow(() -> new BusinessException("Item não pertence a este balanço.", HttpStatus.NOT_FOUND));
        if (balanco.getStatus() != StatusBalanco.EM_ANDAMENTO) {
            throw new ConflictException("Este balanço não está mais em contagem. Nenhuma alteração foi salva.");
        }
        if (dto.quantidadeFisica() == null || dto.quantidadeFisica().signum() < 0) {
            throw new FieldValidationException("quantidadeFisica",
                    "Informe a quantidade física contada (não negativa).");
        }
        conferirVersao(dto.version(), item.getVersion());
        item.definirContagem(dto.quantidadeFisica());
        itensBalanco.flush();
        sincronizacao.registrar(balanco);
        return adapter.toItemDto(item);
    }

    @Transactional
    public BalancoDTO confirmar(Long id, ConfirmarBalancoRequestDTO dto) {
        var balanco = bloquear(id);
        // Carrega os relacionamentos juntos, somente depois de adquirir o bloqueio.
        balanco = encontrar(id);
        conferirVersao(dto.version(), balanco.getVersion());
        if (balanco.getStatus() != StatusBalanco.EM_ANDAMENTO) {
            throw new ConflictException("Confirme apenas um balanço EM_ANDAMENTO.");
        }
        if (balanco.getItens().isEmpty()) {
            throw new ConflictException("Não confirme um balanço sem itens.");
        }
        var versoes = new HashMap<Long, Long>();
        if (dto.itens() == null) throw new FieldValidationException("itens", "Confira as contagens antes de concluir.");
        for (var consultado : dto.itens()) {
            if (consultado == null || consultado.id() == null || consultado.version() == null || versoes.put(consultado.id(), consultado.version()) != null)
                throw new FieldValidationException("itens", "Informe cada item uma única vez, com sua versão consultada.");
        }
        if (versoes.size() != balanco.getItens().size()) throw new ConflictException("A lista de itens mudou. Confira o balanço atualizado.");
        for (var item : balanco.getItens()) {
            if (!versoes.containsKey(item.getId())) throw new ConflictException("A lista de itens mudou. Confira o balanço atualizado.");
            conferirVersao(versoes.get(item.getId()), item.getVersion());
            if (item.getQuantidadeFisica() == null) {
                throw new ConflictException("Confirme todos os itens antes de encerrar o balanço.");
            }
        }
        balanco.setStatus(StatusBalanco.CONCLUIDO);
        alertas.reavaliarDiferenca(balanco);
        alertas.concluirAgendamento();
        repository.flush();
        sincronizacao.registrar(balanco);
        return adapter.toDto(balanco);
    }

    @Transactional
    public BalancoDTO gerarAjustes(Long id, VersaoBalancoRequestDTO dto) {
        var balanco = bloquear(id);
        // Carrega os relacionamentos juntos, somente depois de adquirir o bloqueio.
        balanco = encontrar(id);
        conferirVersao(dto.version(), balanco.getVersion());
        if (balanco.getStatus() != StatusBalanco.CONCLUIDO) {
            throw new ConflictException("Gere ajustes apenas em um balanço CONCLUIDO.");
        }
        for (var item : balanco.getItens()) {
            ajustes.aplicarItem(balanco, item);
        }
        alertas.apagarDiferencas(balanco);
        repository.flush();
        sincronizacao.registrar(balanco);
        return adapter.toDto(balanco);
    }
}
