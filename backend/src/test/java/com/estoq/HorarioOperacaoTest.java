package com.estoq;

import com.estoq.core.helpers.HorarioOperacao;
import com.estoq.business.dashboard.RelatoriosController;
import com.estoq.business.relatorios.RelatorioService;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HorarioOperacaoTest {
    @Test
    void noiteBrasileiraContinuaNoMesmoDiaMesmoComServidorEmUtc() {
        var instante = Instant.parse("2026-10-18T01:00:00Z");
        for (var fusoServidor : new ZoneId[] { ZoneOffset.UTC, ZoneId.of("America/Bahia") }) {
            var horario = new HorarioOperacao(Clock.fixed(instante, fusoServidor));
            var dia = LocalDate.of(2026, 10, 17);
            assertEquals(dia, horario.hoje());
            assertEquals(dia, horario.diaLocal(LocalDateTime.of(2026, 10, 18, 1, 0)));
            assertEquals(LocalDateTime.of(2026, 10, 17, 3, 0), horario.inicioUtc(dia));
        }
    }

    @Test
    void relatorioPadraoIncluiACompraFeitaAsVinteEDuasHoras() {
        var horario = new HorarioOperacao(Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneOffset.UTC));
        var relatorios = mock(RelatorioService.class);
        var controller = new RelatoriosController(horario, relatorios, null, null, null, null);
        controller.cmv(null, null, null);
        verify(relatorios).calcularCmv(LocalDateTime.of(2026, 9, 17, 3, 0), LocalDateTime.of(2026, 10, 18, 3, 0), null);
    }

    @Test
    void limiteDeMesUsaMeiaNoiteLocal() {
        var horario = new HorarioOperacao();
        var fim = horario.inicioUtc(LocalDate.of(2027, 1, 1));
        assertEquals(YearMonth.of(2026, 12), YearMonth.from(horario.diaLocal(fim.minusNanos(1))));
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(horario.diaLocal(fim)));
    }
}
