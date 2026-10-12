package com.estoq.business.auth;

import com.estoq.core.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginConcorrenciaTest {
    @Test
    void reservaNoMaximoCincoTentativasSimultaneas() throws Exception {
        var servico = new LoginAttemptService(5, 15);
        var partida = new CountDownLatch(1);
        var admitidas = new java.util.concurrent.atomic.AtomicInteger();
        try (var tarefas = Executors.newFixedThreadPool(20)) {
            var resultados = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++) resultados.add(tarefas.submit(() -> {
                try {
                    partida.await();
                    servico.reservar("ALVO@TESTE.COM", "127.0.0.1");
                    admitidas.incrementAndGet();
                } catch (BusinessException e) {
                    // As demais requisições encontram o limite já reservado.
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            partida.countDown();
            for (var resultado : resultados) resultado.get(5, TimeUnit.SECONDS);
        }
        assertEquals(5, admitidas.get());
    }

    @Test
    void removeChavesExpiradasSemEsperarEmTempoReal() {
        var horario = new Horario();
        var servico = new LoginAttemptService(2, 15, horario);
        servico.reservar("alvo@teste.com", "ip");
        servico.reservar("ALVO@TESTE.COM", "ip");
        assertThrows(BusinessException.class, () -> servico.reservar("alvo@teste.com", "ip"));
        horario.agora = horario.agora.plus(Duration.ofMinutes(16));
        servico.limparExpiradas();
        assertEquals(0, servico.chavesAtivas());
        assertDoesNotThrow(() -> servico.reservar("alvo@teste.com", "ip"));
    }

    static class Horario extends Clock {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return agora; }
    }
}
