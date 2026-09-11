package com.estoq.business.alertas;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertaService {

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
            repository.apagarAbertos(TipoAlerta.ESTOQUE_BAIXO, produto.getId(), null);
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
                repository.apagarAbertos(TipoAlerta.PROXIMO_VENCIMENTO, produto.getId(), lote.getId());
                repository.apagarAbertos(TipoAlerta.VENCIDO, produto.getId(), lote.getId());
            }
        }
    }

    @Transactional
    public void reavaliarBalancoPendente(ConfiguracaoBalancoModel configuracao) {
        if (configuracao == null || !configuracao.isAtivo()) {
            return;
        }
        if (configuracao.getProximaExecucao() != null && !configuracao.getProximaExecucao().isAfter(LocalDate.now())) {
            gerar(TipoAlerta.BALANCO_PENDENTE, null, null, configuracao.getPeriodicidade());
        }
    }

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void avaliarAgendamentos() {
        for (var configuracao : configuracoesBalanco.findAllByAtivoTrueOrderByIdAsc()) {
            if (configuracoes.estaVencida(configuracao)) {
                gerar(TipoAlerta.BALANCO_PENDENTE, null, null, configuracao.getPeriodicidade());
                configuracao.setProximaExecucao(configuracoes.calcularProxima(configuracao.getPeriodicidade(), configuracao.getDiaExecucao(), LocalDate.now()));
            }
        }
    }

    @Transactional
    public void apagarAbertos(TipoAlerta tipo, Long produtoId, Long loteId) {
        repository.apagarAbertos(tipo, produtoId, loteId);
    }

    @Transactional
    public void reavaliarDiferenca(BalancoModel balanco) {
        for (var item : balanco.getItens()) {
            if (item.getDiferenca().signum() != 0 && item.getQuantidadeFisica() != null) {
                gerar(TipoAlerta.DIFERENCA_ESTOQUE, item.getProduto(), null, balanco.getId());
            }
        }
    }

    @Transactional
    public void gerar(TipoAlerta tipo, ProdutoModel produto, LoteModel lote, Object extra) {
        Long produtoId = produto == null ? null : produto.getId();
        Long loteId = lote == null ? null : lote.getId();
        repository.apagarAbertos(tipo, produtoId, loteId);
        for (Perfil perfil : AlertaAdapter.destinos(tipo)) {
            var alerta = new AlertaModel();
            alerta.setTipo(tipo);
            alerta.setPerfilDestino(perfil);
            alerta.setProduto(produto);
            alerta.setLote(lote);
            alerta.setMensagem(AlertaAdapter.mensagemDe(tipo, produto, lote, extra));
            alerta.setDataGeracao(LocalDateTime.now());
            repository.save(alerta);
        }
    }

    public List<AlertaDTO> listar(Boolean visualizado, Perfil perfil) {
        return repository.findAllByAtivoTrueOrderByDataGeracaoDesc().stream()
                .filter(a -> visualizado == null || a.isVisualizado() == visualizado)
                .filter(a -> a.getPerfilDestino() == perfil)
                .map(adapter::toDto).toList();
    }

    public long contarAbertos() {
        return repository.countByAtivoTrueAndVisualizadoFalse();
    }

    @Transactional
    public AlertaDTO marcarVisualizado(Long id) {
        var alerta = repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Alerta não encontrado.", HttpStatus.NOT_FOUND));
        alerta.setVisualizado(true);
        return adapter.toDto(alerta);
    }
}