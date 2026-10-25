# Integrações externas

Esta pasta guarda configuração de exemplo, documentação e ferramentas locais.
A integração Gmail roda no backend Java, junto dos serviços existentes. Não é
outro servidor e não precisa de deploy próprio.

- [Gmail e confirmação de e-mail](google/README.md)
- [Entrar e criar conta com Google](google/login.md)

São autorizações diferentes: o cliente Web identifica quem entra no EstoQ;
o cliente de computador autoriza apenas o remetente a enviar confirmações.
O acesso Google dos clientes não pede permissão para ler ou enviar e-mails.
