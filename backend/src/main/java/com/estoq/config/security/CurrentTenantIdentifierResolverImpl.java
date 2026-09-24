package com.estoq.config.security;

import com.estoq.business.auth.UsuarioPrincipal;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Define o tenant ativo da requisição para o isolamento {@code @TenantId}.
 * <p>
 * Usuário autenticado de uma loja → id do restaurante dele (filtro ativo). Perfil
 * PLATAFORMA e chamadas sem usuário (registro público, testes) → {@code 0} (root):
 * o filtro é desligado e valores explícitos de {@code restaurante_id} são mantidos no insert.
 */
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<Long> {

    /** Tenant raiz: sem filtro de isolamento (PLATAFORMA e chamadas anônimas). */
    public static final Long ROOT = 0L;

    @Override
    public Long resolveCurrentTenantIdentifier() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal usuario
                && usuario.restauranteId() != null && usuario.restauranteId() > 0) {
            return usuario.restauranteId();
        }
        return ROOT;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return tenantId == null || ROOT.equals(tenantId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}