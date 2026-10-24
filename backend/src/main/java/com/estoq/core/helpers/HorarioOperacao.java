package com.estoq.core.helpers;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Datas de operação seguem o Brasil; os horários gravados continuam em UTC. */
@Component
public class HorarioOperacao {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private final Clock relogio;

    public HorarioOperacao() {
        this(Clock.systemUTC());
    }

    public HorarioOperacao(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDateTime agoraUtc() {
        return LocalDateTime.now(relogio.withZone(ZoneOffset.UTC));
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio.withZone(FUSO));
    }

    public LocalDateTime inicioUtc(LocalDate dia) {
        return dia.atStartOfDay(FUSO).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public LocalDate diaLocal(LocalDateTime horarioUtc) {
        return horarioUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(FUSO).toLocalDate();
    }
}
