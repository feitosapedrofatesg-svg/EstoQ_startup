package com.estoq.business.loginGoogle;

import com.estoq.business.auth.AuthenticatedUserDTO;

public record GoogleResultadoDTO(AuthenticatedUserDTO usuario, boolean confirmacaoEmailPendente, String email) {}
