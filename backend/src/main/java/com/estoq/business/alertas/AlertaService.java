package com.estoq.business.alertas;

import com.estoq.business.movimentacoesEstoque.EstoqueVariosAlteradosEvent;
import com.estoq.business.restaurantes.IRestauranteRepository;
import com.estoq.config.security.CurrentTenantIdentifierResolverImpl;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.sincronizacao.DadosAlteradosService;
import com.estoq.business.sincronizacao.GrupoDados;
import com.estoq.business.balancos.BalancoModel;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoModel;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoService;
import com.estoq.business.configuracoesBalanco.IConfiguracaoBalancoRepository;
import com.estoq.business.lotes.ILoteRepository;
import com.estoq.business.lotes.LoteModel;
import com.estoq.business.lotes.LoteService;
import com.estoq.business.movimentacoesEstoque.EstoqueAlteradoEvent;
import com.estoq.business.produtos.IProdutoRepository;
import com.estoq.business.produtos.ProdutoModel;
import com.estoq.business.usuarios.Perfil;
import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertaService {

    private final HorarioOperacao horario;
    private final CurrentTenantIdentifierResolverImpl tenants;
    private final IRestauranteRepository restaurantes;

    private final DadosAlteradosService sincronizacao;
    private final IAlertaRepository repository;
    private final IProdutoRepository produtos;
    private final LoteService lotes;
    private final ILoteRepository loteRepository;
    private final AlertaAdapter adapter;
    private final IConfiguracaoBalancoRepository configuracoesBalanco;
    private final ConfiguracaoBalancoService configuracoes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reavaliarProduto(EstoqueAlteradoEvent evento) {
        reconciliar(java.util.List.of(evento.produtoId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reavaliarProdutos(EstoqueVariosAlteradosEvent evento) {
        reconciliar(evento.produtoIds());
    }

    /** A leitura também recupera avisos perdidos e detecta a mudança do dia sem movimentação. */
    private void reconciliar(List<Long> ids) {
        var tenantId = tenants.resolveCurrentTenantIdentifier();
        if (tenantId > 0) restaurantes.bloquearParaImportacao(tenantId).orElseThrow();
        var catalogo = ids == null ? produtos.findAllByAtivoTrue() : produtos.findAllByAtivoTrueAndIdIn(ids);
        var estoqueAtual = ids == null ? loteRepository.listarEstoque() : loteRepository.findParaAlertas(ids);
        var existentes = ids == null ? repository.findAllByAtivoTrueOrderByDataGeracaoDesc()
                : repository.findParaProdutos(ids);
        var esperados = new java.util.HashSet<String>();
        var porProduto = estoqueAtual.stream().collect(java.util.stream.Collectors.groupingBy(l -> l.getProduto().getId()));
        for (var produto : catalogo) {
            var lotesProduto = porProduto.getOrDefault(produto.getId(), List.of());
            var saldo = lotesProduto.stream().filter(l -> l.estaDisponivel(horario.hoje()))
                    .map(LoteModel::getQuantidadeAtual).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            var parametro = produto.getParametro();
            if (parametro != null && parametro.isAtivo() && saldo.compareTo(parametro.getEstoqueMinimo()) < 0)
                manterAlerta(TipoAlerta.ESTOQUE_BAIXO, produto, null, parametro.getEstoqueMinimo(), existentes, esperados);
            for (var lote : lotesProduto) {
                if (lote.getQuantidadeAtual().signum() <= 0) continue;
                if (lote.estaVencido(horario.hoje()))
                    manterAlerta(TipoAlerta.VENCIDO, produto, lote, null, existentes, esperados);
                else if (lotes.proximoVencimento(lote))
                    manterAlerta(TipoAlerta.PROXIMO_VENCIMENTO, produto, lote, lote.diasParaVencimento(horario.hoje()), existentes, esperados);
            }
        }
        for (var alerta : existentes) {
            if (operacional(alerta.getTipo()) && !esperados.contains(chave(alerta))) {
                alerta.setAtivo(false);
                sincronizacao.registrar(alerta);
            }
        }
    }

    private boolean operacional(TipoAlerta tipo) {
        return tipo == TipoAlerta.ESTOQUE_BAIXO || tipo == TipoAlerta.PROXIMO_VENCIMENTO || tipo == TipoAlerta.VENCIDO;
    }

    private String chave(AlertaModel alerta) {
        return alerta.getTipo() + ":" + alerta.getPerfilDestino() + ":"
                + (alerta.getProduto() == null ? "" : alerta.getProduto().getId()) + ":"
                + (alerta.getLote() == null ? "" : alerta.getLote().getId());
    }

    private void manterAlerta(TipoAlerta tipo, ProdutoModel produto, LoteModel lote, Object extra,
            List<AlertaModel> existentes, java.util.Set<String> esperados) {
        var mensagem = AlertaAdapter.mensagemDe(tipo, produto, lote, extra);
        for (var perfil : AlertaAdapter.destinos(tipo)) {
            var candidato = new AlertaModel();
            candidato.setTipo(tipo); candidato.setPerfilDestino(perfil);
            candidato.setProduto(produto); candidato.setLote(lote);
            var chave = chave(candidato);
            esperados.add(chave);
            var existente = existentes.stream().filter(a -> chave(a).equals(chave)).findFirst().orElse(null);
            if (existente == null) {
                candidato.setMensagem(mensagem);
                candidato.setDataGeracao(LocalDateTime.now(ZoneOffset.UTC));
                repository.save(candidato);
                sincronizacao.registrar(candidato);
            } else if (!mensagem.equals(existente.getMensagem())) {
                // Atualizar a antecedência não reabre um aviso já marcado como visto.
                existente.setMensagem(mensagem);
                sincronizacao.registrar(existente);
            }
        }
    }

    @Transactional
    public void reavaliarBalancoPendente(ConfiguracaoBalancoModel configuracao) {
        if (configuracao == null || !configuracao.isAtivo() || !configuracoes.estaVencida(configuracao)) {
            return;
        }
        var mensagem = AlertaAdapter.mensagemDe(TipoAlerta.BALANCO_PENDENTE, null, null,
                configuracao.getProximaExecucao().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        var existentes = repository.findAllByAtivoTrueOrderByDataGeracaoDesc();
        for (var perfil : AlertaAdapter.destinos(TipoAlerta.BALANCO_PENDENTE)) {
            if (existentes.stream().anyMatch(a -> a.getTipo() == TipoAlerta.BALANCO_PENDENTE
                    && a.getPerfilDestino() == perfil && mensagem.equals(a.getMensagem()))) {
                continue;
            }
            var alerta = new AlertaModel();
            alerta.setTipo(TipoAlerta.BALANCO_PENDENTE);
            alerta.setPerfilDestino(perfil);
            alerta.setMensagem(mensagem);
            alerta.setDataGeracao(LocalDateTime.now(ZoneOffset.UTC));
            repository.save(alerta);
            sincronizacao.registrar(alerta);
        }
    }

    // Avaliado nas requisições autenticadas, dentro do contexto da cozinha.
    @Transactional
    public void avaliarAgendamentos() {
        var agendamentos = configuracoesBalanco.findAllByAtivoTrueOrderByIdAsc();
        if (agendamentos.isEmpty()) return;
        var mensagensAtuais = agendamentos.stream().filter(configuracoes::estaVencida)
                .map(c -> AlertaAdapter.mensagemDe(TipoAlerta.BALANCO_PENDENTE, null, null,
                        c.getProximaExecucao().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                .toList();
        repository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> a.getTipo() == TipoAlerta.BALANCO_PENDENTE && !a.isVisualizado()
                        && !mensagensAtuais.contains(a.getMensagem()))
                .forEach(a -> { repository.delete(a); sincronizacao.registrar(a); });
        for (var configuracao : agendamentos) {
            reavaliarBalancoPendente(configuracao);
        }
    }

    @Transactional
    public void concluirAgendamento() {
        for (var configuracao : configuracoesBalanco.findAllByAtivoTrueOrderByIdAsc()) {
            if (configuracoes.estaVencida(configuracao)) {
                sincronizacao.registrar(configuracao);
                configuracao.setProximaExecucao(configuracoes.calcularProxima(configuracao.getPeriodicidade(),
                        configuracao.getDiaExecucao(), horario.hoje()));
            }
        }
        apagarAbertos(TipoAlerta.BALANCO_PENDENTE, null, null);
    }

    @Transactional
    public void apagarAbertos(TipoAlerta tipo, Long produtoId, Long loteId) {
        if (repository.apagarAbertos(tipo, produtoId, loteId) > 0)
            sincronizacao.registrarAtual(GrupoDados.ALERTAS);
    }

    @Transactional
    public void reavaliarDiferenca(BalancoModel balanco) {
        // A diferença de todos os itens é avaliada no banco, sem uma inserção por produto.
        apagarDiferencas(balanco);
        if (repository.criarDiferencasDoBalanco(balanco.getId(), balanco.getRestauranteId()) > 0)
            sincronizacao.registrar(balanco.getRestauranteId(), GrupoDados.ALERTAS);
    }

    @Transactional
    public void apagarDiferencas(BalancoModel balanco) {
        if (repository.apagarDiferencasDoBalanco(balanco.getId(), balanco.getRestauranteId()) > 0)
            sincronizacao.registrar(balanco.getRestauranteId(), GrupoDados.ALERTAS);
    }

    @Transactional
    public List<AlertaDTO> listar(Boolean visualizado, Perfil perfil) {
        reconciliar(null);
        avaliarAgendamentos();
        return repository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> visualizado == null || a.isVisualizado() == visualizado)
                .filter(a -> a.getPerfilDestino() == perfil)
                .map(adapter::toDto).toList();
    }

    @Transactional
    public long contarAbertos(Perfil perfil) {
        reconciliar(null);
        avaliarAgendamentos();
        return repository.countByAtivoTrueAndVisualizadoFalseAndPerfilDestino(perfil);
    }

    @Transactional
    public AlertaDTO marcarVisualizado(Long id) {
        var alerta = repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Alerta não encontrado.", HttpStatus.NOT_FOUND));
        alerta.setVisualizado(true);
        sincronizacao.registrar(alerta);
        return adapter.toDto(alerta);
    }
}
