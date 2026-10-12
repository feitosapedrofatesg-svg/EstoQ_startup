package com.estoq.core.services;

import com.estoq.core.domains.TenantEntity;

/** A persistência informa uma alteração sem conhecer seu transporte para o navegador. */
public interface NotificadorAlteracoes {
    void registrar(TenantEntity entidade);
}
