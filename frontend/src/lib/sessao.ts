/** A geração invalida também respostas que ainda estavam em trânsito no logout. */
let geracao = 0;
let identidade = "anonimo";

export function sessaoAtual(): string {
  return `${identidade}|${geracao}`;
}

export function mudarSessao(novaIdentidade: string): void {
  identidade = novaIdentidade;
  geracao++;
}
