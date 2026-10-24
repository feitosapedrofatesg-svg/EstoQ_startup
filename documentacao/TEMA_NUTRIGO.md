# EstoQ — cores e tipografia Nutrigo

## Origem dos valores

Referência indicada: Nutrigo — Health Nutrition & Diet Admin Dashboard,
arquivo Figma `BzjQmqhbjay05Vy8XzTOHz`, nó inicial `2:21017`.
A integração desta sessão não expôs propriedades do arquivo. O primeiro CSS
fornecido descrevia uma imagem; a implementação só começou após o usuário
fornecer o CSS dos componentes editáveis da prancha **Component**.

Os valores abaixo vêm dessa exportação fornecida pelo usuário, não de amostragem
de imagem nem de uma inspeção direta do Figma. O mapeamento desses valores para
os componentes existentes do EstoQ é uma decisão de aplicação, não uma cópia do
dashboard Nutrigo.

## Valores confirmados na exportação e utilizados

| Cor | Uso no EstoQ |
| --- | --- |
| `#FFFFFF` | Menu lateral, cards e painéis |
| `#F9F4F2` | Fundo principal e hover neutro; Cream-BG da referência |
| `#EEEEEF` | Botões secundários, campos e controles segmentados |
| `#F6F6F7` | Campos desabilitados |
| `#E1E1E2` | Separadores, bordas e mensagens sobre fundo escuro |
| `#757D83` | Bordas de campos: mais contraste que os separadores |
| `#8A8C90` | Bordas em hover; não usado como texto pequeno sobre branco |
| `#272932` | Texto principal e texto sobre o verde claro |
| `#52545B` | Texto secundário, placeholders, ícones e foco de campos |
| `#C2E66E` | Ações primárias, navegação ativa e seleção |
| `#DFF9A2` | Hover das ações primárias e fundos de sucesso |
| `#FFCB65` | Avatar e ícone de aviso nas notificações |
| `#FFEDC9` | Fundo de avisos |
| `#FF1A36` | Ícone de erro sobre fundo escuro e gradiente de erro existente |
| `#1F3045` / `#F3FAFF` | Texto/fundo informativos |
| `#D2EBFF` / `#BEBFC2` | Ícone informativo e controle de fechar nas notificações |

As cores roxas das bordas tracejadas dos conjuntos de componentes do Figma não
foram importadas: pertencem à organização da prancha, não ao tema do produto.
O logotipo original do EstoQ, inclusive suas cores, foi preservado.

### Tipografia

**Poppins**, servida localmente por `@fontsource/poppins`, sem requisições ao
Google Fonts. Foram carregados apenas os pesos normais confirmados:

- **400:** corpo do texto e badges;
- **500:** menus, botões, filtros e rótulos;
- **600:** títulos, valores e ênfases.

Os tamanhos, as alturas de linha e o espaçamento entre letras continuam os do
EstoQ. A **Figtree** aparece nos nomes das seções da prancha de componentes e em
um rótulo oculto; não há evidência suficiente para adotá-la como fonte geral do
aplicativo. A marca não foi recriada com nenhuma das fontes.

## Ajustes de aplicação e legibilidade — não são valores exatos do Figma

O cinza `#8A8C90` sobre branco tem contraste de aproximadamente **3,37:1**;
para textos pequenos/placeholder foi escolhido `#52545B`, também presente na
exportação. O verde escuro original `#73A107` sobre branco tem **3,07:1** e
precisou ser escurecido para links e texto de sucesso.

| Cor ajustada | Finalidade |
| --- | --- |
| `#4F6F05` | Links e texto de sucesso; 5,82:1 sobre branco e 5,05:1 sobre `#DFF9A2` |
| `#3F5904` | Hover do botão de sucesso |
| `#7A5000` | Texto de aviso; 6,12:1 sobre `#FFEDC9` |
| `#BE1328` | Texto/fundo forte de erro; 6,34:1 com texto branco |
| `#A11022` | Hover do botão destrutivo |
| `#FFF0F2` | Fundo suave de erro; contraste de 5,73:1 com `#BE1328` |

O anel de foco mantém os **3 px** existentes e usa `rgba(39, 41, 50, 0.65)`.
Nas notificações escuras, o mesmo anel usa `rgba(194, 230, 110, 0.85)` para
continuar visível sobre o fundo, sem alterar sua geometria.
O topo translúcido e os backdrops usam os novos tons mantendo suas opacidades
anteriores. Essas combinações/transparências e a escolha do verde claro para
hover são adaptações do EstoQ: não foram exportadas como estados do Figma.

Sucesso, aviso e erro mantêm cores distintas, texto e ícones existentes. A
opacidade original dos botões desabilitados foi mantida; controles inativos não
estão sujeitos ao requisito AA de contraste para texto ativo.

### Laranja da marca nas notificações

O laranja mais forte do tema anterior, preservado no logotipo, foi reaplicado a
pedido. Esses dois valores não fazem parte da exportação Nutrigo:

| Cor | Finalidade |
| --- | --- |
| `#EF7320` | Ponto de notificação do sino; uso decorativo, 2,94:1 sobre branco |
| `#C85200` | Texto de “Marcar como visto”; 4,50:1 sobre branco, variação escura para leitura |

O ponto continua sem número visível; a contagem permanece no `aria-label` e na
lista de alertas. O hover da ação usa `--warn-bg` (`#FFEDC9`).

## Relatórios e central de alertas

- **Desperdício:** as barras horizontais deixaram o acento verde `#C2E66E` e
  passaram a usar `--bad` (`#BE1328`), com trilho `--bad-bg` (`#FFF0F2`). O total
  do bloco já era exibido em vermelho. A mudança vale só para o bloco de
  desperdício; sucesso e aviso continuam verdes e âmbar.
- **Central de alertas:** o botão preserva 44 × 44 px e raio de 12 px, agora com
  superfície branca, borda de 1 px transparente e ícone `#52545B`. O contador
  visual foi substituído por um ponto de 8 px no laranja forte `#EF7320`.
  A ação “Marcar como visto” usa `#C85200`; a quantidade de alertas continua no
  `aria-label` do botão e no painel ao abrir. O ponto é absoluto e não participa
  do fluxo, portanto não desloca o cabeçalho.

## Escopo preservado

O JSX recebeu apenas o tom semântico das barras de desperdício e o ponto
decorativo do sino; conteúdo, imagens, ícones, navegação, APIs, autenticação,
backend e banco não mudaram. Nenhuma coordenada ou dimensão do frame Figma foi
utilizada. Tamanhos de layout, espaçamentos, alinhamentos, responsividade, raios,
espessuras de borda e animações permanecem iguais; a única dimensão decorativa
alterada é o ponto do sino, de 19 px para 8 px, para seguir a referência.

As sombras decorativas foram preservadas, inclusive a cor original da sombra
do botão de recalcular CMV, desacoplada do novo acento em `--shadow-accent`.
As cores específicas da folha de impressão foram mantidas; ela recebe apenas
a nova família e os pesos tipográficos disponíveis.

## Conferências

- Build TypeScript/Vite e cinco testes de configuração Vercel.
- Suíte completa de interface: **88 testes Playwright aprovados**.
- Oito testes novos: fonte/acentos em 320, 390 e 1440 px, contraste AA dos
  pares verificados, distinção dos estados, hover, foco e seleção, barras de
  desperdício em vermelho, botão de alertas com ponto da referência e ação de
  marcar alerta como visto no laranja forte.
- Suíte de responsividade existente: 320, 390, 720, 768, 1024, 1180 e 1440 px.
- Comparação antes/depois em **21 combinações de tela/largura**, com imagens de
  login, dashboard, catálogo e relatórios em desktop e celular.
- Auditoria estrutural de **342 regras CSS**: nenhuma alteração de medidas,
  tamanho de fonte, altura de linha, sombra decorativa ou animação.

### Quebras adicionais, sem compensação de layout

A substituição da fonte altera a largura natural dos glifos. Não foram
detectados novos cortes nos elementos acompanhados nem excesso horizontal
da página. Entretanto, a 320 px passaram a ocupar duas linhas:

- “CMV mês a mês no período”;
- “Agendamento do balanço”;
- “Nota fiscal em PDF, XML, JPG ou PNG”;
- “Conteúdo de cada unidade da nota”.

No login desktop, o convite “Crie a conta agora” também ganhou uma quebra.
Esses efeitos foram registrados, sem reduzir fonte, alterar largura,
espaçamento ou altura de linha para escondê-los. A candidata deve ser revisada
na homologação antes de promover o mesmo código à produção.
