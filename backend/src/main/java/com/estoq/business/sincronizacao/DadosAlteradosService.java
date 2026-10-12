package com.estoq.business.sincronizacao;

import com.estoq.core.domains.TenantEntity;
import com.estoq.core.services.NotificadorAlteracoes;
import com.estoq.business.auth.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DadosAlteradosService implements NotificadorAlteracoes {
    private final ApplicationEventPublisher eventos;
    private final Object recurso = new Object();

    @Override
    public void registrar(TenantEntity entidade) {
        var grupo = switch (org.hibernate.Hibernate.getClass(entidade).getSimpleName()) {
            case "BalancoModel", "ItemBalancoModel" -> GrupoDados.BALANCOS;
            case "ProdutoModel", "CategoriaModel", "ParametroEstoqueModel" -> GrupoDados.CATALOGO;
            case "UsuarioModel" -> GrupoDados.USUARIOS;
            case "ConfiguracaoBalancoModel" -> GrupoDados.CONFIGURACOES_BALANCO;
            case "AlertaModel" -> GrupoDados.ALERTAS;
            case "ParametroCmvModel", "CmvReceitaBaseModel" -> GrupoDados.CMV;
            default -> GrupoDados.ESTOQUE;
        };
        registrar(entidade.getRestauranteId(), grupo);
    }

    public void registrarAtual(GrupoDados grupo) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) registrar(p.restauranteId(), grupo);
    }

    /** Uma notificação por restaurante e transação; rollback não publica nada. */
    @SuppressWarnings("unchecked")
    public void registrar(Long restauranteId, GrupoDados grupo) {
        if (restauranteId == null || restauranteId <= 0 || !TransactionSynchronizationManager.isActualTransactionActive()) return;
        var grupos = (Map<Long, EnumSet<GrupoDados>>) TransactionSynchronizationManager.getResource(recurso);
        if (grupos == null) {
            grupos = new HashMap<>();
            var acumulados = grupos;
            TransactionSynchronizationManager.bindResource(recurso, acumulados);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void suspend() { TransactionSynchronizationManager.unbindResource(recurso); }
                @Override public void resume() { TransactionSynchronizationManager.bindResource(recurso, acumulados); }
                @Override public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.getResource(recurso) == acumulados)
                        TransactionSynchronizationManager.unbindResource(recurso);
                }
            });
        }
        var conjunto = grupos.get(restauranteId);
        if (conjunto == null) {
            conjunto = EnumSet.of(grupo);
            grupos.put(restauranteId, conjunto);
            eventos.publishEvent(new DadosAlteradosEvent(restauranteId, conjunto));
        } else conjunto.add(grupo);
    }
}
