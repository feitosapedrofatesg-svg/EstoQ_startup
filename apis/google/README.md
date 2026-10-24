# Gmail API — remetente de confirmação

Remetente escolhido: **estoq.sistema@gmail.com**. Só essa conta autoriza o envio.
Os usuários do EstoQ confirmam qualquer e-mail válido; não precisam de Gmail.

O backend renova o access token e envia mensagens MIME pela Gmail API. Usa
Java HttpClient e o Jackson já existente, sem SDK ou servidor adicional.
Os links expiram em 24 horas. Abrir o link não confirma nada: há um botão que
faz POST com CSRF. O banco guarda SHA-256, nunca o token original.

## Preparar a autorização (dono da conta)

1. No Google Cloud, crie/selecione um projeto e ative a Gmail API.
2. Configure o consentimento OAuth e o escopo `https://www.googleapis.com/auth/gmail.send`.
   Se estiver em teste, cadastre `estoq.sistema@gmail.com` como usuário de teste.
3. Crie um cliente OAuth **Aplicativo de computador**. Baixe o JSON para fora
   deste repositório. Não envie esse arquivo, senhas ou tokens na conversa.
4. No computador com navegador, execute:

   ```bash
   python3 apis/google/autorizar_gmail.py --credenciais /caminho/fora-do-git/cliente.json
   ```

   Escolha a conta `estoq.sistema@gmail.com`. O script confere `state`, usa PKCE
   e recebe o código por loopback local. Grava os valores em
   `~/.config/estoq/gmail.env`, com permissão 600, sem mostrar os tokens.
5. Cadastre os valores nas variáveis do backend Render. `APP_PUBLIC_URL` é o
   endereço do frontend do ambiente (homologação usa sua URL própria).
   Não são variáveis `VITE_*`. Mantenha `EMAIL_CONFIRMACAO_ENABLED=false`.

Clientes externos em status **Testing** podem ter refresh tokens com duração
de 7 dias com esse escopo. Antes de ativar para clientes, ajuste o status e as
exigências de verificação OAuth indicadas pelo Google. Revogar a autorização
ou mudar configurações da conta também pode exigir nova autorização.

## Ativar após verificar o remetente

Primeiro publique backend e frontend com a função desligada. Em homologação,
configure OAuth, ative `EMAIL_CONFIRMACAO_ENABLED=true`, cadastre um e-mail de
teste e confira remetente, entrega, spam, confirmação, reenvio e login. Não use
produção como teste de cadastro. Depois do aceite, ative em produção com sua
URL pública. A configuração ativa sem credenciais/URL válida impede o boot.

A mensagem só sai após commit, em fila de até 50 pedidos e duas threads, com
timeout de conexão de 5 s e de pedido de 10 s. Falhas não desfazem o cadastro;
o usuário pode pedir reenvio. Sem fila durável, restart pode perder envios
pendentes; o reenvio é a recuperação prevista nesta etapa.

## Acesso e limites

Contas anteriores mantêm acesso sem serem marcadas como confirmadas. Com a
função ativa, cadastro e mudança efetiva de e-mail exigem confirmação; trocar
apenas letras maiúsculas/espaços não dispara o fluxo. Troca de senha isolada
não exige nova confirmação. Desativar a função não libera contas já pendentes.
O bootstrap da conta PLATAFORMA continua um procedimento administrativo.

Reenvio responde uma mensagem genérica para conta existente, ausente ou já
confirmada. Limites por endereço são persistidos: um envio/minuto e cinco/hora.
O limite por IP usa o controle em memória existente (30 reenvios/hora e 60
confirmações/hora). Os limites por IP continuam restritos a uma instância.
Token anterior perde validade no reenvio; alteração de endereço invalida o
vínculo com o endereço antigo. Tokens expirados são limpos após sete dias.

A confirmação comprova posse da caixa postal. Não verifica identidade ou CNPJ.
O painel mostra pendência; nenhuma senha ou token aparece na API de usuários.

- [Envio MIME/base64url](https://developers.google.com/workspace/gmail/api/guides/sending)
- [Escopos Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [OAuth para aplicativos locais](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Validade de refresh tokens](https://developers.google.com/identity/protocols/oauth2#expiration)
