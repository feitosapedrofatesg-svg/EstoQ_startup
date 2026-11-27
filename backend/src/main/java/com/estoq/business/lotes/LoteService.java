package com.estoq.business.lotes;

import com.estoq.core.helpers.HorarioOperacao;

import com.estoq.business.sincronizacao.DadosAlteradosService;

import com.estoq.business.produtos.ProdutoModel;
import com.estoq.core.exceptions.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoteService {

    private final HorarioOperacao horario;

    private final DadosAlteradosService sincronizacao;
    private final ILoteRepository repository;
    private final LoteAdapter adapter;

    public LoteModel encontrar(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new BusinessException("Lote não encontrado.", HttpStatus.NOT_FOUND));
    }

    public LoteDTO buscar(Long id) {
        return adapter.toDto(encontrar(id));
    }

    public org.springframework.data.domain.Page<LoteDTO> pagina(String q,boolean vencidos,boolean somenteComSaldo,Long produtoId,int pagina,int tamanho) {
        return repository.pagina(q.trim(),vencidos,somenteComSaldo,produtoId,horario.hoje(),
            org.springframework.data.domain.PageRequest.of(Math.max(0,pagina),Math.clamp(tamanho,1,100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("dataValidade").nullsLast(),
                    org.springframework.data.domain.Sort.Order.asc("id")))).map(adapter::toDto);
    }

    public List<LoteDTO> listar(Long produtoId) {
        return repository.listarEstoque().stream()
                .filter(l -> produtoId == null || produtoId.equals(l.getProduto().getId()))
                .map(adapter::toDto).toList();
    }

    public List<LoteDTO> disponiveis(Long produtoId) {
        return repository.findDisponiveisBaixa(produtoId, horario.hoje()).stream().map(adapter::toDto).toList();
    }

    public List<LoteDTO> vencidos() {
        return repository.listarEstoque().stream()
                .filter(l -> l.getQuantidadeAtual().signum() > 0 && l.estaVencido(horario.hoje()))
                .map(adapter::toDto).toList();
    }

    public List<LoteDTO> proximosVencimento() {
        return repository.listarEstoque().stream().filter(this::proximoVencimento).map(adapter::toDto).toList();
    }

    public boolean proximoVencimento(LoteModel lote) {
        return proximoVencimento(lote,horario.hoje());
    }

    public static boolean proximoVencimento(LoteModel lote, LocalDate hoje) {
        var par = lote.getProduto().getParametro();
        if (par == null || !par.isAtivo() || !lote.estaDisponivel(hoje)) {
            return false;
        }
        var dias = lote.diasParaVencimento(hoje);
        return dias != null && dias <= par.getDiasAlertaVencimento();
    }

    @Transactional
    public LoteModel criar(ProdutoModel produto, BigDecimal inicial, BigDecimal saldo, BigDecimal preco, LocalDate validade) {
        return criar(produto, inicial, saldo, preco, validade, null);
    }

    @Transactional
    public LoteModel criar(ProdutoModel produto, BigDecimal inicial, BigDecimal saldo, BigDecimal preco, LocalDate validade, String fabricante) {
        String numero = fabricante == null || fabricante.isBlank() ? null : fabricante.trim();
        if (numero != null && numero.length() > 50)
            throw new com.estoq.core.exceptions.FieldValidationException("loteFabricante", "O lote do fabricante deve ter até 50 caracteres.");
        var lote = new LoteModel();
        lote.setLoteFabricante(numero);
        lote.setProduto(produto);
        lote.setQuantidadeInicial(inicial);
        lote.setQuantidadeAtual(saldo);
        lote.setPrecoUnitario(preco);
        lote.setDataEntrada(horario.hoje());
        lote.setDataValidade(validade);
        lote.setCodigo("PENDENTE-" + UUID.randomUUID());
        repository.saveAndFlush(lote);
        sincronizacao.registrar(lote);
        lote.setCodigo(String.format("L-%06d", lote.getId()));
        return lote;
    }
}
