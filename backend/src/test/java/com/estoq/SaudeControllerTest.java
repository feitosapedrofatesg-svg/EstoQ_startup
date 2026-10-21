package com.estoq;

import com.estoq.config.SaudeController;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SaudeControllerTest {
    @Test
    void verificacaoDoProcessoNaoAcessaBanco() {
        var banco = mock(DataSource.class);
        assertEquals("ok", new SaudeController(banco).vivo().get("status"));
        assertEquals("local", new SaudeController(banco).vivo().get("ambiente"));
        verifyNoInteractions(banco);
    }

    @Test
    void identificaAmbienteSemConsultarBancoOuExporCredenciais() {
        var banco = mock(DataSource.class);
        var controller = new SaudeController(banco);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "ambiente", "homologacao");
        assertEquals("homologacao", controller.vivo().get("ambiente"));
        assertEquals(3, controller.vivo().size());
        // status, versão e ambiente são os únicos dados públicos.
        verifyNoInteractions(banco);
    }

    @Test
    void prontidaoDetectaBancoIndisponivelSemExporConexao() throws Exception {
        var banco = mock(DataSource.class);
        when(banco.getConnection()).thenThrow(new SQLException("host e senha privados"));
        var resposta = new SaudeController(banco).pronto();
        assertEquals(503, resposta.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "indisponivel"), resposta.getBody());
    }
}
