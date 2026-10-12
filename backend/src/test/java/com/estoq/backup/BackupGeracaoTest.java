package com.estoq.backup;

import com.estoq.core.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupGeracaoTest {
    @TempDir Path pasta;

    BackupConfig config() {
        return new BackupConfig(pasta.toString(), "localhost", 5432, "teste", "", "usuario", "segredo", 7, "", "", "");
    }

    class Simulado extends BackupService {
        boolean falhar;
        boolean timeout;
        CountDownLatch iniciou = new CountDownLatch(1);
        CountDownLatch liberar;
        Process processo = mock(Process.class);
        Simulado() { super(config()); }
        @Override protected Process iniciar(ProcessBuilder builder) throws IOException {
            var cmd = builder.command();
            Files.writeString(Path.of(cmd.get(cmd.indexOf("--file") + 1)), "dump fictício");
            when(processo.exitValue()).thenReturn(falhar ? 1 : 0);
            iniciou.countDown();
            return processo;
        }
        @Override protected boolean aguardar(Process processo) throws InterruptedException {
            if (liberar != null) liberar.await(5, TimeUnit.SECONDS);
            return !timeout;
        }
    }

    @Test void publicaSomenteArquivosCompletosComNomesUnicos() throws Exception {
        var servico = new Simulado();
        var a = servico.gerar();
        var b = servico.gerar();
        assertNotEquals(a.nome(), b.nome());
        assertEquals(2, servico.listar().size());
        assertEquals("dump fictício", Files.readString(servico.baixar(a.nome())));
        Files.writeString(pasta.resolve("estoq-20260101-000000.dump.partial"), "incompleto");
        assertEquals(2, servico.listar().size());
        assertThrows(BusinessException.class, () -> servico.baixar("../segredo"));
    }

    @Test void falhaETimeoutRemovemArquivosParciais() throws Exception {
        var servico = new Simulado();
        servico.falhar = true;
        assertThrows(BusinessException.class, servico::gerar);
        servico.falhar = false;
        servico.timeout = true;
        when(servico.processo.isAlive()).thenReturn(true);
        assertThrows(BusinessException.class, servico::gerar);
        verify(servico.processo).destroyForcibly();
        assertTrue(servico.listar().isEmpty());
        try (var arquivos = Files.list(pasta)) { assertEquals(0, arquivos.count()); }
    }

    @Test void bloqueiaSegundaGeracaoEnquantoAPrimeiraEstaEmAndamento() throws Exception {
        var servico = new Simulado();
        servico.liberar = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var primeira = executor.submit(servico::gerar);
            assertTrue(servico.iniciou.await(5, TimeUnit.SECONDS));
            assertTrue(servico.listar().isEmpty());
            assertThrows(BusinessException.class, servico::gerar);
            servico.liberar.countDown();
            assertNotNull(primeira.get(5, TimeUnit.SECONDS));
        }
    }
}
