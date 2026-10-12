package com.estoq;

import com.estoq.backup.BackupService;
import com.estoq.backup.BackupDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupPermissoesIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean BackupService backups;
    @TempDir Path pasta;

    @Test
    void perfisDeRestauranteNaoAcessamNenhumaOperacaoDeBackup() throws Exception {
        for (var perfil : List.of("ADMIN", "COZINHA", "NUTRICIONISTA")) {
            mvc.perform(get("/api/backups").with(user("usuario").roles(perfil))).andExpect(status().isForbidden());
            mvc.perform(post("/api/backups").with(user("usuario").roles(perfil)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/api/backups/estoq-20260101-120000.dump").with(user("usuario").roles(perfil))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(backups);
    }

    @Test
    void plataformaListaGeraEBaixaArquivoSemRemoverCsrf() throws Exception {
        var nome = "estoq-20260101-120000.dump";
        var arquivo = pasta.resolve(nome);
        Files.writeString(arquivo, "dump de teste");
        var dto = new BackupDTO(nome, Files.size(arquivo), Instant.now());
        when(backups.listar()).thenReturn(List.of(dto));
        when(backups.gerar()).thenReturn(dto);
        when(backups.baixar(nome)).thenReturn(arquivo);
        mvc.perform(get("/api/backups").with(user("plataforma").roles("PLATAFORMA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nome").value(nome));
        mvc.perform(post("/api/backups").with(user("plataforma").roles("PLATAFORMA"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/backups").with(user("plataforma").roles("PLATAFORMA")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string("dump de teste"));
        mvc.perform(get("/api/backups/" + nome).with(user("plataforma").roles("PLATAFORMA")))
                .andExpect(status().isOk()).andExpect(content().string("dump de teste"));
        verify(backups, times(1)).gerar();
    }
}
