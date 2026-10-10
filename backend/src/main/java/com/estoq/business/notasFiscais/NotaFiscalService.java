package com.estoq.business.notasFiscais;

import com.estoq.business.auth.UsuarioAtual;
import com.estoq.business.entradas.EntradaRequestDTO;
import com.estoq.business.entradas.EntradaService;
import com.estoq.business.produtos.*;
import com.estoq.core.exceptions.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class NotaFiscalService {
    private final INotaFiscalRepository repository;
    private final IVinculoFornecedorRepository vinculos;
    private final IProdutoRepository catalogo;
    private final ProdutoService produtos;
    private final EntradaService entradas;
    private final UsuarioAtual usuarioAtual;
    private final LeitorDocumentoNota leitor;
    private final NotaFiscalValidation validation;
    private final NotaFiscalAdapter adapter;
    private final AssociadorProdutoNota associador;
    private final RateioNota rateio;

    @Transactional
    public NotaFiscalDTO analisar(List<MultipartFile> arquivos) {
        var dados = leitor.ler(arquivos);
        validation.validarLeitura(dados);
        var existente = repository.findByChaveAndAtivoTrue(dados.chave()).orElse(null);
        if (existente!=null) return adapter.toDto(existente);
        var nota = new NotaFiscalModel();
        nota.setChave(dados.chave()); nota.setFornecedor(dados.fornecedor());
        nota.setDocumentoFornecedor(dados.documentoFornecedor()); nota.setNumero(dados.numero());
        nota.setSerie(dados.serie()); nota.setEmissao(dados.emissao()); nota.setValorTotal(dados.valorTotal());
        nota.setUsuarioId(usuarioAtual.obter().getId());
        var avisos = new ArrayList<>(dados.avisos());
        var somaLiquida = dados.itens().stream().map(i -> i.valorBruto().subtract(i.desconto())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (somaLiquida.compareTo(dados.valorTotal())!=0)
            avisos.add("Ajuste rateado para fechar o total da nota: " + dados.valorTotal().subtract(somaLiquida).toPlainString() + ". Confira descontos, frete e despesas.");
        nota.setAvisos(String.join("\n",avisos));
        var valores = rateio.calcular(dados);
        var ativos = catalogo.findAllByAtivoTrue();
        for(int i=0;i<dados.itens().size();i++) {
            var lido = dados.itens().get(i);
            var item = new ItemNotaFiscalModel();
            item.setNota(nota); item.setNumero(lido.numero()); item.setCodigo(lido.codigo());
            item.setDescricao(lido.descricao()); item.setCodigoBarras(AssociadorProdutoNota.codigoBarrasValido(lido.codigoBarras()));
            item.setUnidadeNota(lido.unidade()); item.setQuantidadeNota(lido.quantidade());
            item.setValorBruto(lido.valorBruto()); item.setDesconto(lido.desconto());
            item.setValorEntrada(valores.get(i));
            item.setValorRateado(valores.get(i).subtract(lido.valorBruto().subtract(lido.desconto())));
            item.setValidade(lido.validade()); item.setIncerto(lido.incerto());
            associador.sugerir(item,dados.documentoFornecedor(),ativos);
            nota.getItens().add(item);
        }
        repository.saveAndFlush(nota);
        return adapter.toDto(nota);
    }

    public NotaFiscalDTO buscar(Long id) { return adapter.toDto(encontrar(id)); }

    @Transactional
    public NotaFiscalDTO confirmar(Long id, ConfirmarNotaDTO dto) {
        // O bloqueio da nota torna o clique duplo idempotente, mesmo em requisições concorrentes.
        var nota = repository.bloquear(id).orElseThrow(() -> new BusinessException("Nota não encontrada.",HttpStatus.NOT_FOUND));
        if (nota.isConfirmada()) return adapter.toDto(nota);
        if (!Objects.equals(dto.version(),nota.getVersion())) throw new ConflictException();
        if (!dto.custosConferidos()) throw new FieldValidationException("custosConferidos","Confira o total, os descontos e o rateio da nota.");
        var decisoes = new HashMap<Long,ConfirmarNotaDTO.Item>();
        for (var item : dto.itens()) if (decisoes.put(item.id(),item)!=null)
            throw new FieldValidationException("itens","Item repetido na confirmação.");
        if (decisoes.size()!=nota.getItens().size() || nota.getItens().stream().anyMatch(i -> !decisoes.containsKey(i.getId())))
            throw new FieldValidationException("itens","Envie a revisão de todos os itens da nota.");
        if (dto.itens().stream().noneMatch(ConfirmarNotaDTO.Item::selecionado))
            throw new FieldValidationException("itens","Selecione pelo menos um item.");
        // O custo de linhas desmarcadas permanece separado; não aumenta o custo dos itens escolhidos.
        var totalRevisado = nota.getItens().stream().map(i -> {
            var escolha = decisoes.get(i.getId());
            return escolha.selecionado() ? escolha.valorEntrada() : i.getValorEntrada();
        }).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        if (totalRevisado.compareTo(nota.getValorTotal())!=0)
            throw new FieldValidationException("valorEntrada","A soma dos custos revisados deve fechar o total da nota, incluindo itens desmarcados.");
        var novosNaNota = new HashMap<String,Long>();
        for (var item : nota.getItens()) {
            var escolha = decisoes.get(item.getId());
            item.setSelecionado(escolha.selecionado());
            if (!escolha.selecionado()) continue;
            // A conferência final da nota cobre todas as linhas e embalagens.
            validation.validarItem(escolha);
            Long produtoId = escolha.produtoId();
            if (produtoId==null) {
                var chaveProduto = AssociadorProdutoNota.normalizar(escolha.nomeProduto()) + "|" + escolha.unidadeEstoque() + "|" + escolha.categoriaId();
                produtoId = novosNaNota.get(chaveProduto);
                if (produtoId==null) {
                    var iguais = catalogo.findAllByAtivoTrue().stream().filter(p -> AssociadorProdutoNota.normalizar(p.getNome())
                            .equals(AssociadorProdutoNota.normalizar(escolha.nomeProduto())) && p.getUnidadeMedida()==escolha.unidadeEstoque()).toList();
                    if (!iguais.isEmpty()) throw new ConflictException("Já existe um produto com esse nome e unidade. Selecione-o na revisão.");
                    var novo = new ProdutoDTO(); novo.setNome(escolha.nomeProduto()); novo.setCategoriaId(escolha.categoriaId());
                    novo.setUnidadeMedida(escolha.unidadeEstoque()); novo.setCodigoBarras(item.getCodigoBarras());
                    produtoId = produtos.criar(novo).getId(); novosNaNota.put(chaveProduto,produtoId);
                }
            }
            var produto = produtos.encontrar(produtoId);
            if (produto.getUnidadeMedida()!=escolha.unidadeEstoque())
                throw new FieldValidationException("unidadeEstoque","A unidade deve ser a mesma do produto selecionado.");
            var quantidade = validation.quantidade(escolha);
            var resultado = entradas.registrarEntrada(new EntradaRequestDTO(produtoId,quantidade,escolha.valorEntrada(),
                    escolha.unidadeEstoque(),escolha.validade(),"NF-e " + nota.getNumero() + "/" + nota.getSerie()
                    + " • " + nota.getFornecedor() + " • item " + item.getNumero(),escolha.valorEntrada().signum()==0));
            item.setProdutoId(produtoId); item.setCategoriaId(produto.getCategoria().getId()); item.setNomeProduto(produto.getNome());
            item.setUnidadeEstoque(escolha.unidadeEstoque()); item.setFator(escolha.fator());
            item.setQuantidadeEntrada(quantidade); item.setValorEntrada(escolha.valorEntrada());
            item.setValidade(escolha.validade()); item.setEmbalagemConfirmada(true);
            item.setMovimentacaoId(resultado.movimentacoes().getFirst().id());
            salvarVinculo(nota,item);
        }
        nota.setConfirmada(true); nota.setUsuarioId(usuarioAtual.obter().getId());
        repository.flush();
        return adapter.toDto(nota);
    }

    private void salvarVinculo(NotaFiscalModel nota, ItemNotaFiscalModel item) {
        var vinculo = vinculos.findByDocumentoFornecedorAndCodigoAndAtivoTrue(nota.getDocumentoFornecedor(),item.getCodigo()).orElseGet(VinculoFornecedorModel::new);
        vinculo.setDocumentoFornecedor(nota.getDocumentoFornecedor()); vinculo.setCodigo(item.getCodigo());
        vinculo.setDescricao(item.getDescricao()); vinculo.setUnidadeNota(item.getUnidadeNota());
        vinculo.setProdutoId(item.getProdutoId()); vinculo.setFator(item.getFator()); vinculo.setUnidadeEstoque(item.getUnidadeEstoque());
        vinculos.save(vinculo);
    }
    private NotaFiscalModel encontrar(Long id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new BusinessException("Nota não encontrada.",HttpStatus.NOT_FOUND));
    }
}
