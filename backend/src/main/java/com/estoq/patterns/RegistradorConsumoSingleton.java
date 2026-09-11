package com.estoq.patterns;

import com.estoq.business.consumos.ConsumoRequestDTO;
import com.estoq.business.consumos.ConsumoService;
import com.estoq.business.movimentacoesEstoque.MovimentacaoResultadoDTO;

public class RegistradorConsumoSingleton {

    private static volatile RegistradorConsumoSingleton instancia;

    private final ConsumoService consumos;

    private RegistradorConsumoSingleton(ConsumoService consumos) {
        this.consumos = consumos;
    }

    public static RegistradorConsumoSingleton obter(ConsumoService consumos) {
        var atual = instancia;
        if (atual != null) {
            return atual;
        }
        synchronized (RegistradorConsumoSingleton.class) {
            if (instancia == null) {
                instancia = new RegistradorConsumoSingleton(consumos);
            }
            return instancia;
        }
    }

    public MovimentacaoResultadoDTO registrar(ConsumoRequestDTO dto) {
        return consumos.registrarConsumo(dto);
    }
}