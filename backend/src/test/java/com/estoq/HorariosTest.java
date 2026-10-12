package com.estoq;

import com.estoq.core.domains.BaseModel;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HorariosTest {

    private static class Registro extends BaseModel {
        void criar() {
            registrarCriacao();
        }
    }

    @Test
    void gravaEmUtcMesmoQuandoServidorEstaNoFusoDaBahia() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Bahia"));
            var antes = LocalDateTime.now(ZoneOffset.UTC);
            var registro = new Registro();
            registro.criar();
            var depois = LocalDateTime.now(ZoneOffset.UTC);
            assertFalse(registro.getDataHoraCriacao().isBefore(antes));
            assertFalse(registro.getDataHoraCriacao().isAfter(depois));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
