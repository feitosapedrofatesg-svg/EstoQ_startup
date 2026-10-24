package com.estoq.business.dashboard;

import com.estoq.core.exceptions.FieldValidationException;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.business.produtosAbertos.ProdutoAbertoDTO;
import com.estoq.business.relatorios.CmvMensalDTO;
import com.estoq.business.relatorios.CmvReceitaBaseDTO;
import com.estoq.business.relatorios.CmvReceitaBaseService;
import com.estoq.business.relatorios.CmvResumoDTO;
import com.estoq.business.relatorios.FaturamentoDiarioDTO;
import com.estoq.business.relatorios.FaturamentoPeriodoDTO;
import jakarta.validation.Valid;
import com.estoq.business.relatorios.ConsumoDiaSemanaDTO;
import com.estoq.business.relatorios.ConsumoMedioDTO;
import com.estoq.business.relatorios.DesperdicioAgregadoDTO;
import com.estoq.business.relatorios.DesperdicioDTO;
import com.estoq.business.relatorios.RelatorioService;
import com.estoq.business.relatorios.RelatorioPdfService;
import com.estoq.business.relatorios.ReposicaoSugeridaDTO;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RelatoriosController {

    private final HorarioOperacao horario;

    private final RelatorioService relatorios;
    private final RelatorioPdfService pdfs;
    private final com.estoq.business.relatorios.RelatorioExportacaoService exportacao;
    private final DashboardService dashboard;
    private final CmvReceitaBaseService receitasCmv;

    @GetMapping("/api/relatorios/estoque-atual")
    public List<EstoqueDTO> estoqueAtual() {
        return relatorios.estoqueAtual();
    }

    @GetMapping("/api/relatorios/proximos-vencimento")
    public List<LoteDTO> proximosVencimento() {
        return relatorios.proximosVencimento();
    }

    @GetMapping("/api/relatorios/vencidos")
    public List<LoteDTO> vencidos() {
        return relatorios.vencidos();
    }

    @GetMapping("/api/relatorios/produtos-abertos")
    public List<ProdutoAbertoDTO> produtosAbertos() {
        return relatorios.produtosAbertos();
    }

    @GetMapping("/api/relatorios/desperdicio")
    public List<DesperdicioDTO> desperdicio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.desperdicio(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/desperdicio/agregado")
    public List<DesperdicioAgregadoDTO> desperdicioAgregado(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.desperdicioAgregado(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/consumo-medio")
    public List<ConsumoMedioDTO> consumoMedio(
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        if (dias < 1 || dias > 3660)
            throw new FieldValidationException("dias", "Informe de 1 a 3660 dias.");
        var fimAt = horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1));
        return relatorios.consumoMedio(horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1).minusDays(dias)), fimAt);
    }

    @GetMapping("/api/relatorios/consumo/dia-semana")
    public List<ConsumoDiaSemanaDTO> consumoDiaSemana(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.consumoPorDiaSemana(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/cmv")
    public CmvResumoDTO cmv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) BigDecimal receitaBase) {
        return relatorios.calcularCmv(definirInicio(inicio), definirFim(fim), receitaBase);
    }

    @GetMapping("/api/relatorios/cmv/receita-base")
    public CmvReceitaBaseDTO receitaBaseCmv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return receitasCmv.obter(inicio, fim);
    }

    @PutMapping("/api/relatorios/cmv/receita-base")
    public CmvReceitaBaseDTO salvarReceitaBaseCmv(@RequestBody CmvReceitaBaseDTO dto) {
        return receitasCmv.salvar(dto);
    }

    @GetMapping("/api/relatorios/cmv/mensal")
    public List<CmvMensalDTO> cmvMensal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.cmvPorMes(definirInicio(inicio), definirFim(fim));
    }

    @GetMapping("/api/relatorios/faturamento-diario")
    public FaturamentoPeriodoDTO faturamentoDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return receitasCmv.faturamento(inicio, fim);
    }

    @PutMapping("/api/relatorios/faturamento-diario")
    public FaturamentoDiarioDTO salvarFaturamentoDiario(@Valid @RequestBody FaturamentoDiarioDTO dto) {
        return receitasCmv.salvarDia(dto);
    }

    @GetMapping("/api/relatorios/reposicao-sugerida")
    public List<ReposicaoSugeridaDTO> reposicaoSugerida(
            @RequestParam(required = false) Integer dias) {
        return relatorios.reposicaoSugerida(dias);
    }

    @GetMapping(value = "/api/relatorios/pdf/{tipo}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "30") long dias,
            @RequestParam(required = false) BigDecimal receitaBase) {
        var bytes = pdfs.gerar(tipo, inicio, fim, dias, receitaBase);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=relatorio-" + tipo + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/api/relatorios/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam String tipo, @RequestParam String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "30") long dias, @RequestParam(required = false) BigDecimal receitaBase) {
        var arquivo = exportacao.gerar(tipo, formato, inicio, fim, dias, receitaBase);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + arquivo.nome())
                .body(arquivo.conteudo());
    }

    @GetMapping("/api/dashboard/resumo")
    public DashboardResumoDTO resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) BigDecimal receitaBase) {
        var diaFinal = fim == null ? horario.hoje() : fim;
        var diaInicial = inicio == null ? diaFinal.minusDays(29) : inicio;
        return dashboard.resumo(horario.inicioUtc(diaInicial), horario.inicioUtc(diaFinal.plusDays(1)), receitaBase);
    }

    private LocalDateTime definirInicio(LocalDate inicio) {
        if (inicio != null) {
            return horario.inicioUtc(inicio);
        }
        return horario.inicioUtc(horario.hoje().minusDays(30));
    }

    private LocalDateTime definirFim(LocalDate fim) {
        return horario.inicioUtc((fim == null ? horario.hoje() : fim).plusDays(1));
    }

}
