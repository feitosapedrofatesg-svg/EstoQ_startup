export type Perfil = "ADMIN" | "COZINHA" | "NUTRICIONISTA" | "PLATAFORMA";
export type UnidadeMedida = "KG" | "G" | "L" | "ML" | "UN";

export interface ConsumoLoteResultadoDTO {
  estoque: EstoqueDTO[];
  movimentacoes: MovimentacaoDTO[];
}
export type MotivoDesperdicio =
  | "VENCIMENTO"
  | "DETERIORACAO"
  | "PREPARO_INCORRETO"
  | "SOBRA_NAO_APROVEITADA"
  | "OUTRO";
export type TipoMovimentacao = "ENTRADA" | "CONSUMO" | "DESPERDICIO" | "AJUSTE";
export type StatusBalanco = "PENDENTE" | "EM_ANDAMENTO" | "CONCLUIDO";
export type TipoBalanco = "GERAL" | "PARCIAL";
export type PeriodicidadeBalanco = "DIARIA" | "SEMANAL" | "MENSAL";
export type TipoAlerta =
  | "ESTOQUE_BAIXO"
  | "PROXIMO_VENCIMENTO"
  | "VENCIDO"
  | "BALANCO_PENDENTE"
  | "DIFERENCA_ESTOQUE";

export interface ErrorResponse {
  title?: string;
  message?: string;
  motive?: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  empty: boolean;
}

export interface BaseDTO {
  id: number;
  version: number;
  ativo: boolean;
  dataHoraCriacao: string;
}

export interface AuthenticatedUserDTO {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  /** Loja em que a tela está operando. Nulo para o perfil PLATAFORMA. */
  restauranteNome: string | null;
  restauranteId: number | null;
}

/** Cadastro aberto de um novo restaurante na plataforma. */
export interface RegistroRequest {
  nomeLoja: string;
  nomeResponsavel: string;
  email: string;
  senha: string;
}

/** Restaurante listado no painel da plataforma. */
export interface RestauranteDTO {
  id: number;
  nome: string;
  ativo: boolean;
  dataHoraCriacao: string;
  totalUsuarios: number;
}

export interface UsuarioResponseDTO {
  id: number;
  version: number;
  nome: string;
  email: string;
  perfil: Perfil;
  ativo: boolean;
  dataHoraCriacao: string;
}

export interface CategoriaDTO extends BaseDTO {
  nome: string;
  descricao?: string | null;
}

export interface ProdutoDTO extends BaseDTO {
  nome: string;
  unidadeMedida: UnidadeMedida;
  categoriaId: number;
  categoriaNome?: string | null;
  codigoBarras?: string | null;
  parametroEstoqueId?: number | null;
  saldoAtual?: number | null;
}

export interface ParametroEstoqueDTO extends BaseDTO {
  produtoId: number;
  tempoReposicaoDias: number;
  periodoAnaliseDias: number;
  consumoMedioDiario: number | null;
  estoqueMinimo: number | null;
  estoqueMedio: number | null;
  estoqueMaximo: number | null;
  diasAlertaVencimento: number;
  dataAtualizacao: string;
}

export interface LoteDTO {
  id: number;
  version: number;
  codigo: string;
  produtoId: number;
  produtoNome: string;
  unidadeMedida: UnidadeMedida;
  quantidadeInicial: number;
  quantidadeAtual: number;
  dataEntrada: string;
  dataValidade: string | null;
  precoUnitario: number;
  vencido: boolean;
  disponivel: boolean;
  diasParaVencimento: number | null;
}

export interface EstoqueDTO {
  produtoId: number;
  produtoNome: string;
  categoriaNome: string;
  unidadeMedida: UnidadeMedida;
  saldoAtual: number;
  saldoDisponivelConsumo: number;
  valorEstoque: number;
  estoqueMinimo: number | null;
  estoqueMedio: number | null;
  estoqueMaximo: number | null;
  abaixoDoMinimo: boolean;
  possuiItensAbertos: boolean;
}

export interface MovimentacaoDTO {
  unidadeMedida?: UnidadeMedida;
  id: number;
  tipo: TipoMovimentacao;
  dataHora: string;
  produtoId: number;
  produtoNome: string;
  loteId: number | null;
  loteCodigo: string | null;
  loteVersion: number | null;
  quantidadeLoteAtual: number | null;
  usuarioId: number | null;
  produtoAbertoId: number | null;
  quantidade: number;
  quantidadeAnterior: number | null;
  quantidadePosterior: number | null;
  precoUnitario: number | null;
  custoConsumo: number | null;
  valorPrejuizo: number | null;
  valorTotalPago: number | null;
  diferencaApurada: number | null;
  motivo: string | null;
  descricaoMotivo: string | null;
  observacao: string | null;
}

export interface MovimentacaoResultadoDTO {
  produtoId: number;
  saldoAtual: number;
  movimentacoes: MovimentacaoDTO[];
}

export interface ProdutoAbertoDTO {
  id: number;
  version: number;
  produtoId: number;
  produtoNome: string;
  unidadeMedida: string;
  loteId: number;
  loteCodigo: string;
  dataValidade: string | null;
  usuarioId: number | null;
  dataAbertura: string;
  quantidadeAberta: number;
  quantidadeUtilizada: number;
  quantidadeRestante: number;
  finalizado: boolean;
  quantidadeLoteAtual: number;
  saldoAtual: number;
}

export interface ItemBalancoDTO {
  id: number;
  version: number;
  produtoId: number;
  produtoNome: string;
  unidadeMedida: string;
  categoriaId: number | null;
  categoriaNome: string | null;
  quantidadeSistema: number;
  quantidadeFisica: number | null;
  diferenca: number | null;
  ajusteAplicado: boolean;
}

export interface BalancoDTO {
  id: number;
  version: number;
  dataHora: string;
  tipo: TipoBalanco;
  status: StatusBalanco;
  usuarioId: number;
  usuarioNome: string;
  categoriaIds: number[];
  categoriaNomes: string[];
  itens: ItemBalancoDTO[];
}

export interface ConfiguracaoBalancoDTO {
  id: number;
  version: number;
  periodicidade: PeriodicidadeBalanco;
  diaExecucao: number | null;
  proximaExecucao: string | null;
}

export interface AlertaDTO {
  id: number;
  tipo: TipoAlerta;
  mensagem: string;
  perfilDestino: string;
  dataGeracao: string;
  visualizado: boolean;
  produtoId: number | null;
  produtoNome: string | null;
  loteId: number | null;
  loteCodigo: string | null;
}

export interface CmvResumoDTO {
  dataInicio: string;
  dataFim: string;
  valorEstoqueInicial: number;
  valorCompras: number;
  valorEstoqueFinal: number;
  cmv: number;
  receitaBase: number | null;
  cmvPercentual: number | null;
  faturamentoInformado: number;
  diasFaturamentoPendentes: number;
  receitaPorPeriodo: boolean;
  percentualIdeal: number | null;
  diferencaPercentualParaMeta: number | null;
  valorConsumoRegistrado: number;
  valorDesperdicio: number;
  percentualDesperdicioSobreCmv: number;
  valorPerdasNaoExplicadas: number;
}

export interface CmvReceitaBaseDTO {
  inicio: string;
  fim: string;
  receitaBase: number | null;
}

export interface CmvMensalDTO {
  periodo: string;
  cmv: number;
  valorCompras: number;
  valorDesperdicio: number;
}

export interface DashboardResumoDTO {
  totalProdutosAtivos: number;
  produtosEstoqueBaixo: number;
  lotesProximosVencimento: number;
  lotesVencidos: number;
  produtosAbertosAtivos: number;
  balancosPendentes: number;
  valorDesperdicioPeriodo: number;
  cmvPeriodo: number;
  cmvPercentual: number | null;
  cmvIdeal: number | null;
  diferencaCmvParaMeta: number | null;
  perdasNaoExplicadasPeriodo: number;
  receitaBase: number | null;
  faturamentoInformado: number;
  diasFaturamentoPendentes: number;
  receitaPorPeriodo: boolean;
}

export interface FaturamentoDiarioDTO { data: string; valor: number | null; version: number | null }
export interface FaturamentoPeriodoDTO {
  inicio: string; fim: string; totalInformado: number; diasInformados: number; diasPendentes: number;
  receitaBase: number | null; receitaPorPeriodo: boolean; dias: FaturamentoDiarioDTO[];
}

export interface DesperdicioDTO {
  dataHora: string;
  motivo: MotivoDesperdicio;
  descricaoMotivo: string | null;
  produtoId: number;
  produtoNome: string;
  loteCodigo: string | null;
  quantidade: number;
  valorPrejuizo: number;
  unidadeMedida: string;
}

export interface DesperdicioAgregadoDTO {
  produtoId: number;
  produtoNome: string;
  motivo: MotivoDesperdicio;
  quantidade: number;
  valorPrejuizo: number;
}

export interface ConsumoMedioDTO {
  produtoId: number;
  produtoNome: string;
  unidadeMedida: string;
  totalConsumidoPeriodo: number;
  diasPeriodo: number;
  consumoMedioDiario: number;
}

export interface ConsumoDiaSemanaDTO {
  diaSemana: string;
  valorTotal: number;
  valorMedio: number;
}

export interface ReposicaoSugeridaDTO {
  produtoId: number;
  produtoNome: string;
  categoriaNome: string;
  unidadeMedida: UnidadeMedida;
  saldoAtual: number;
  estoqueMinimo: number;
  consumoMedioDiario: number;
  diasReposicao: number;
  quantidadeSugerida: number;
}

export interface BackupDTO {
  nome: string;
  tamanhoBytes: number;
  criadoEm: string;
}

export interface ParametroCmvDTO {
  id: number;
  version: number;
  percentualIdeal: number;
}

export interface ItemNotaFiscalDTO {
  id: number; numero: number; codigo: string; descricao: string; unidadeNota: string;
  quantidadeNota: number; valorBruto: number; desconto: number; valorRateado: number; valorEntrada: number;
  produtoId: number | null; categoriaId: number | null; nomeProduto: string; unidadeEstoque: UnidadeMedida;
  fator: number; quantidadeEntrada: number | null; validade: string | null;
  selecionado: boolean; embalagemConfirmada: boolean; incerto: boolean; movimentacaoId: number | null;
  origemCategoria?: string | null; motivoCategoria?: string | null; revisarCategoria?: boolean;
}
export interface NotaFiscalDTO {
  id: number; version: number; chave: string; fornecedor: string; documentoFornecedor: string;
  numero: string; serie: string; emissao: string | null; valorTotal: number; confirmada: boolean;
  avisos: string[]; itens: ItemNotaFiscalDTO[];
}
