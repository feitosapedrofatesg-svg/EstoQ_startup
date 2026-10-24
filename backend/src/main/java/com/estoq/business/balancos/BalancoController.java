package com.estoq.business.balancos;

import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoDTO;
import com.estoq.business.configuracoesBalanco.ConfiguracaoBalancoService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BalancoController {

    private final BalancoService service;
    private final ConfiguracaoBalancoService configuracoes;

    @GetMapping("/api/balancos")
    public List<BalancoDTO> listar() {
        return service.listar();
    }

    @GetMapping("/api/balancos/{id}")
    public BalancoDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/api/balancos")
    public ResponseEntity<BalancoDTO> criar(@Valid @RequestBody(required = false) CriarBalancoRequestDTO dto) {
        return ResponseEntity.status(201).body(service.criar(dto == null ? new CriarBalancoRequestDTO(null) : dto));
    }

    @PostMapping("/api/balancos/{id}/iniciar")
    public BalancoDTO iniciar(@PathVariable Long id, @Valid @RequestBody VersaoBalancoRequestDTO dto) {
        return service.iniciar(id, dto);
    }

    @PutMapping("/api/balancos/{id}/itens/{itemId}/contagem")
    public ItemBalancoDTO contagem(@PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody ContagemRequestDTO dto) {
        return service.registrarContagem(id, itemId, dto);
    }

    @PostMapping("/api/balancos/{id}/contagens/sistema")
    public BalancoDTO preencherContagensComSistema(@PathVariable Long id,
            @Valid @RequestBody VersaoBalancoRequestDTO dto) {
        return service.preencherContagensComSistema(id, dto);
    }

    @PostMapping("/api/balancos/{id}/confirmar")
    public BalancoDTO confirmar(@PathVariable Long id, @Valid @RequestBody ConfirmarBalancoRequestDTO dto) {
        return service.confirmar(id, dto);
    }

    @PostMapping("/api/balancos/{id}/gerar-ajustes")
    public BalancoDTO gerarAjustes(@PathVariable Long id, @Valid @RequestBody VersaoBalancoRequestDTO dto) {
        return service.gerarAjustes(id, dto);
    }

    @GetMapping("/api/balancos/proximo")
    public List<ConfiguracaoBalancoDTO> proximo() {
        return configuracoes.listar();
    }

    @GetMapping("/api/configuracoes-balanco")
    public List<ConfiguracaoBalancoDTO> listarConfiguracoes() {
        return configuracoes.listar();
    }

    @PostMapping("/api/configuracoes-balanco")
    public ResponseEntity<ConfiguracaoBalancoDTO> criarConfiguracao(@Valid @RequestBody ConfiguracaoBalancoDTO dto) {
        return ResponseEntity.status(201).body(configuracoes.salvar(dto));
    }

    @PutMapping("/api/configuracoes-balanco/{id}")
    public ConfiguracaoBalancoDTO atualizarConfiguracao(@PathVariable Long id,
            @Valid @RequestBody ConfiguracaoBalancoDTO dto) {
        return configuracoes.salvar(new ConfiguracaoBalancoDTO(id, dto.version(), dto.periodicidade(),
                dto.diaExecucao(), dto.proximaExecucao()));
    }

    @DeleteMapping("/api/configuracoes-balanco/{id}")
    public void excluirConfiguracao(@PathVariable Long id) {
        configuracoes.excluir(id);
    }
}