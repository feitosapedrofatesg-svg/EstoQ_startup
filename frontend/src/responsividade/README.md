# Responsividade

Este módulo reúne o layout e as adaptações de tamanho do frontend. `main.tsx`
importa `index.css` depois de `styles.css`, que conserva fontes, cores e aparência.

- `desktop/`: layout base e dimensões dos componentes, usados como referência em todas as telas.
- `tablet/`: regras intermediárias, ajustes até 1280 px e medição das oito linhas visíveis nos relatórios.
- `celular/`: regras até 720 px, tabelas em cartões até 1024 px e geração dos rótulos de cada célula.
- `acessibilidade.css`: regras comuns de ponteiro touch e movimento reduzido.

Os arquivos `legado.css` preservam as adaptações existentes. Os arquivos
`ajustes.css` corrigem encaixe, quebra de texto, áreas de toque e espaço útil.
A ordem dos imports de `index.css` faz parte da cascata e deve ser preservada.
Não crie cópias das páginas para cada aparelho: CSS adapta a mesma estrutura
e mantém estados, permissões e ações. Novas regras de tamanho devem ficar aqui.

Até 1024 px, `DataTable` mantém sua tabela no DOM e exibe linhas em cartões,
com rótulos vindos dos cabeçalhos. Cabeçalhos continuam disponíveis para leitores
de tela. Entre 1025 e 1280 px, as tabelas distribuem e quebram o conteúdo.
Nomes extensos não exigem rolagem lateral. O desktop mantém sua organização.

Modais usam a altura dinâmica (`dvh`), rolagem vertical no corpo e áreas seguras
do aparelho. A navegação conserva o menu lateral existente. Relatórios continuam
mostrando oito itens antes da rolagem, calculando a altura das linhas adaptadas.

`tests/responsividade.spec.ts` verifica telas operacionais, revisão de nota,
login, cadastro, plataforma e backup com dados longos e larguras de 320 a 1440 px.
Os dados de teste são simulados; a verificação não movimenta estoque real.
