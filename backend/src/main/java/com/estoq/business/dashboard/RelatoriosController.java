package com.estoq.business.dashboard;

import com.estoq.business.lotes.LoteDTO;
import com.estoq.business.produtos.EstoqueDTO;
import com.estoq.business.produtosAbertos.ProdutoAbertoDTO;
import com.estoq.business.relatorios.CmvMensalDTO;
import com.estoq.business.relatorios.CmvResumoDTO;
import com.estoq.business.relatorios.ConsumoDiaSemanaDTO;
import com.estoq.business.relatorios.ConsumoMedioDTO;
import com.estoq.business.relatorios.DesperdicioAgregadoDTO;
import com.estoq.business.relatorios.DesperdicioDTO;
import com.estoq.business.relatorios.RelatorioService;
import com.estoq.business.relatorios.RelatorioPdfService;
import com.estoq.business.relatorios.ReposicaoSugeridaDTO;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RelatoriosController {

    private final RelatorioService relatorios;
    private final RelatorioPdfService pdfs;
    private final DashboardService dashboard;

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
        var fimAt = (fim == null ? LocalDate.now() : fim).atStartOfDay().plusDays(1);
        return relatorios.consumoMedio(fimAt.minusDays(Math.max(1, dias)), fimAt);
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

    @GetMapping("/api/relatorios/cmv/mensal")
    public List<CmvMensalDTO> cmvMensal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.cmvPorMes(definirInicio(inicio), definirFim(fim));
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

    @GetMapping("/api/dashboard/resumo")
    public DashboardResumoDTO resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) BigDecimal receitaBase) {
        return dashboard.resumo(definirInicio(inicio), definirFim(fim), receitaBase);
    }

    private static LocalDateTime definirInicio(LocalDate inicio) {
        if (inicio != null) {
            return inicio.atStartOfDay();
        }
        return LocalDate.now().minusDays(30).atStartOfDay();
    }

    private static LocalDateTime definirFim(LocalDate fim) {
        return (fim == null ? LocalDate.now() : fim).atStartOfDay().plusDays(1);
    }
}