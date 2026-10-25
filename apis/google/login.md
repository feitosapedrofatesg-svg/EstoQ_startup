# Login com Google

O backend usa OpenID Connect pelo Spring Security. O navegador abre a autorização
do Google e volta ao backend, que valida assinatura, emissor, público, validade,
`state` e `nonce`. A troca do código também usa PKCE. Depois disso, o acesso usa
a mesma sessão, CSRF, perfis e isolamento de restaurantes do login com senha.

## Contas

- Conta nova: informa nome da cozinha e do responsável. Cria um administrador e
  os mesmos parâmetros/categorias do cadastro local. Não inventa uma senha.
- E-mail já cadastrado: precisa confirmar a senha atual do EstoQ uma vez para
  vincular o Google. O restaurante, o perfil e o hash da senha são preservados.
- Conta vinculada: a identificação é pelo `sub` estável do Google. O e-mail não
  é usado para vincular silenciosamente nem para mudar o cadastro existente.
- Gmail e Workspace verificados: a confirmação do endereço vem do Google.
  Outros endereços precisam também do link de confirmação do EstoQ. Sem o envio
  configurado, o cadastro desses endereços fica indisponível, sem criar a conta.
- Usuário desativado ou restaurante suspenso: acesso bloqueado nas duas formas.

Antes de concluir um cadastro/vínculo, a identidade fica apenas na sessão do
servidor por dez minutos. Não há acesso às telas internas nessa etapa.
Os tokens Google não são persistidos no banco nem no armazenamento do navegador.
As autorizações temporárias do cliente OAuth são removidas após o retorno.

A migração V13 acrescenta `google_sub` único e permite senha nula. A restrição
do banco exige pelo menos uma credencial. Não altera senhas existentes.
Alterar o e-mail de uma conta vinculada remove o vínculo e exige confirmação
do novo endereço. Se ela só usa Google, também exige definir uma senha.

## Clientes Web e retornos

Use clientes do tipo **Aplicativo da Web**, distintos do Desktop da Gmail API.
Cada ambiente tem seu próprio cliente e segredo. Para uma operação pública,
o Google orienta separar também os projetos de teste e produção.

| Ambiente | Origem JavaScript | URI de redirecionamento autorizada |
| --- | --- | --- |
| Homologação | `https://estoq-homologacao.vercel.app` | `https://estoq-homologacao.vercel.app/api/login/oauth2/code/google` |
| Produção | `https://estoq-oficial.vercel.app` | `https://estoq-oficial.vercel.app/api/login/oauth2/code/google` |
| Local, se usado | `http://localhost:5174` | `http://localhost:5174/api/login/oauth2/code/google` |

**Origem não recebe caminho nem barra final.** O caminho `/api/login/...` entra
somente no campo de redirecionamento. O domínio deve ser o frontend, pois o
proxy `/api` mantém o cookie da sessão no mesmo site.

Baixe os JSONs para fora do Git. Configure somente no backend Render:

```dotenv
GOOGLE_LOGIN_ENABLED=true
GOOGLE_LOGIN_CLIENT_ID=CLIENTE_WEB_DESTE_AMBIENTE
GOOGLE_LOGIN_CLIENT_SECRET=SEGREDO_WEB_DESTE_AMBIENTE
APP_PUBLIC_URL=https://estoq-homologacao.vercel.app
```

Copie nomes de variáveis do [exemplo](.env.example). Nunca use `VITE_*` para
segredos. `GOOGLE_CLIENT_*` e `GOOGLE_REFRESH_TOKEN` continuam exclusivos do
remetente Gmail; não substitua esses valores pelos clientes Web.
Com `GOOGLE_LOGIN_ENABLED=false`, o botão fica oculto e o login local continua.

## Endpoints

| Método e caminho | Uso |
| --- | --- |
| `GET /api/auth/google/configuracao` | Informa apenas se o botão está ativo. |
| `GET /api/oauth2/authorization/google` | Inicia a autorização pelo navegador. |
| `GET /api/login/oauth2/code/google` | Retorno tratado pelo Spring; não chamar manualmente. |
| `GET /api/auth/google/pendente` | Etapa, nome e e-mail da identidade validada na sessão. |
| `POST /api/registro/google` | Conclui cadastro com `nomeLoja` e `nomeResponsavel`. |
| `POST /api/auth/google/vincular` | Confirma `senha` da conta existente. |
| `POST /api/auth/google/cancelar` | Remove a pendência da sessão. |

Os POSTs exigem CSRF. Cadastro compartilha o limite por IP do cadastro local;
o vínculo também passa pelo limite de tentativas de senha existente.
Nenhum deles aceita `sub`, e-mail, perfil ou restaurante enviados pelo frontend.

## Publicar e conferir

1. Validar build, testes e CI na `dev`.
2. Promover o mesmo commit para `homologacao`, configurar seu cliente Web e
   conferir `/api/saude/vivo`, `/api/saude/pronto` e o botão público.
3. Testar no navegador o retorno real do Google: conta nova, vínculo com conta
   existente, logout e novo acesso. Usar somente dados de teste na homologação.
4. Promover o commit aprovado para `main`, configurar o cliente de produção e
   repetir a verificação de saúde/retornos. Não copiar banco nem variáveis em lote.

Os testes usam um provedor OIDC HTTP local com assinatura RSA, sem conta Google
real. Exercitam código de autorização, PKCE, credenciais inválidas, `state`,
`nonce`, assinatura, emissor, público, expiração, cadastro e vínculo. Os testes
de interface usam API simulada, incluindo celular e desktop.

Se o retorno mostrar erro, confira cliente Web, URI exata, URL pública e cookie
seguro/SameSite=Lax. `APP_PUBLIC_URL` não deve apontar diretamente para o Render.
Não registre códigos, tokens nem segredos nos logs para investigar.

## Publicação do projeto Google

No Google Auth Platform, complete os dados reais de Branding e depois use
**Público-alvo → Publicar app**. Publicar e concluir a verificação são etapas
diferentes; siga as exigências que o console apresentar para os escopos usados.

As páginas públicas do EstoQ podem ser abertas sem sessão:

- Nome do aplicativo: **EstoQ**.
- Página inicial: `https://estoq-oficial.vercel.app/sobre`.
- Política de Privacidade: `https://estoq-oficial.vercel.app/privacidade`.
- E-mail de suporte: `estoq.sistema@gmail.com`, se disponível no seletor do projeto.
  O console exige uma conta elegível; não preencha um endereço que não controla.
- Domínio autorizado: o domínio do ambiente, sem `https://` nem caminho.

Use esses links de produção somente depois de publicar e conferir as páginas.
Enquanto estiver validando a versão, as mesmas rotas existem na homologação.
Se o Google exigir comprovação de domínio, siga o processo de verificação do
proprietário; não use um domínio de terceiros ou termos de uso inexistentes.

Somente `openid`, `email` e `profile` são pedidos no login. Esses escopos têm
exceção ao prazo de sete dias do modo Testing. O escopo `gmail.send` não tem
essa exceção. Para o remetente, publique/regularize o projeto, renove a
autorização e só então ative a confirmação em produção. Veja o [guia Gmail](README.md).

Fontes: [Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/advanced.html),
[cliente Web Google](https://developers.google.com/identity/protocols/oauth2/web-server),
[identidade verificada](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token),
[público e validade das autorizações](https://support.google.com/cloud/answer/15549945),
[configuração para produção](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance).

O ícone do botão e a fonte Google Sans vêm dos [recursos oficiais de marca](https://developers.google.com/identity/branding-guidelines)
e são servidos localmente. A fonte contém somente o texto do botão.
