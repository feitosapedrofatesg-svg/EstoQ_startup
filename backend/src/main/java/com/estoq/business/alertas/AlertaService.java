package com.estoq.business.alertas;

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
import com.estoq.business.produtos.EstoqueService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertaService {

    private final DadosAlteradosService sincronizacao;
    private final IAlertaRepository repository;
    private final IProdutoRepository produtos;
    private final EstoqueService estoque;
    private final LoteService lotes;
    private final ILoteRepository loteRepository;
    private final AlertaAdapter adapter;
    private final IConfiguracaoBalancoRepository configuracoesBalanco;
    private final ConfiguracaoBalancoService configuracoes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reavaliarProduto(EstoqueAlteradoEvent evento) {
        var produto = produtos.findByIdAndAtivoTrue(evento.produtoId()).orElse(null);
        if (produto == null) {
            return;
        }
        var parametro = produto.getParametro();
        var saldo = estoque.obterSaldo(produto.getId());
        if (parametro != null && saldo.compareTo(parametro.getEstoqueMinimo()) < 0) {
            gerar(TipoAlerta.ESTOQUE_BAIXO, produto, null, parametro.getEstoqueMinimo());
        } else {
            apagarAbertos(TipoAlerta.ESTOQUE_BAIXO, produto.getId(), null);
        }
        for (var lote : loteRepository.listarEstoque()) {
            if (!produto.getId().equals(lote.getProduto().getId())) {
                continue;
            }
            if (lote.getQuantidadeAtual().signum() == 0) {
                continue;
            }
            if (lote.estaVencido(LocalDate.now())) {
                gerar(TipoAlerta.VENCIDO, produto, lote, null);
            } else if (lotes.proximoVencimento(lote)) {
                gerar(TipoAlerta.PROXIMO_VENCIMENTO, produto, lote, lote.diasParaVencimento(LocalDate.now()));
            } else {
                apagarAbertos(TipoAlerta.PROXIMO_VENCIMENTO, produto.getId(), lote.getId());
                apagarAbertos(TipoAlerta.VENCIDO, produto.getId(), lote.getId());
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
                        configuracao.getDiaExecucao(), LocalDate.now()));
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
    public void gerar(TipoAlerta tipo, ProdutoModel produto, LoteModel lote, Object extra) {
        Long produtoId = produto == null ? null : produto.getId();
        Long loteId = lote == null ? null : lote.getId();
        apagarAbertos(tipo, produtoId, loteId);
        for (Perfil perfil : AlertaAdapter.destinos(tipo)) {
            var alerta = new AlertaModel();
            alerta.setTipo(tipo);
            alerta.setPerfilDestino(perfil);
            alerta.setProduto(produto);
            alerta.setLote(lote);
            alerta.setMensagem(AlertaAdapter.mensagemDe(tipo, produto, lote, extra));
            alerta.setDataGeracao(LocalDateTime.now(ZoneOffset.UTC));
            repository.save(alerta);
            sincronizacao.registrar(alerta);
        }
    }

    @Transactional
    public List<AlertaDTO> listar(Boolean visualizado, Perfil perfil) {
        avaliarAgendamentos();
        return repository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> visualizado == null || a.isVisualizado() == visualizado)
                .filter(a -> a.getPerfilDestino() == perfil)
                .map(adapter::toDto).toList();
    }

    @Transactional
    public long contarAbertos(Perfil perfil) {
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
