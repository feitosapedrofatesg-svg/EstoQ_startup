package com.estoq.business.auth;

import com.estoq.core.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginConcorrenciaTest {
    @Test
    void variarEmailNaoContornaLimiteDaOrigem() {
        var servico = new LoginAttemptService(5, 15);
        for (int i = 0; i < 100; i++) servico.reservar("pessoa" + i + "@teste.com", "origem");
        assertThrows(BusinessException.class, () -> servico.reservar("outra@teste.com", "origem"));
        assertDoesNotThrow(() -> servico.reservar("outra@teste.com", "outra-origem"));
    }

    @Test
    void variarOrigemNaoContornaLimiteDaConta() {
        var servico = new LoginAttemptService(5, 15);
        for (int i = 0; i < 15; i++) servico.reservar("alvo@teste.com", "origem" + i);
        assertThrows(BusinessException.class, () -> servico.reservar("ALVO@teste.com", "outra"));
    }

    @Test
    void limiteDeMemoriaRecusaChavesNovasSemApagarBloqueiosAtivos() {
        var limites = new LimiteRequisicoes();
        for (int i = 0; i < 10_000; i++) limites.reservar("chave" + i, 1, Duration.ofMinutes(15));
        assertThrows(BusinessException.class, () -> limites.reservar("nova", 1, Duration.ofMinutes(15)));
        assertThrows(BusinessException.class, () -> limites.reservar("chave0", 1, Duration.ofMinutes(15)));
        assertEquals(10_000, limites.tamanho());
    }

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
