import { PaginaPublica } from "../components/PaginaPublica";

export function Privacidade() {
  return <PaginaPublica>
    <h1>Política de Privacidade</h1>
    <p className="muted">Atualizada em 25 de outubro de 2026.</p>
    <p>Esta página explica o tratamento de dados na aplicação EstoQ, voltada ao controle de estoque e CMV de restaurantes.
      O contato do responsável pela aplicação é <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a>.</p>
    <section>
      <h2>Dados utilizados</h2>
      <p>O cadastro contém nome, e-mail, restaurante e perfil de acesso. A operação pode incluir produtos,
        fornecedores, notas fiscais, lotes, movimentações, balanços e faturamentos informados pela equipe.</p>
      <p>Essas informações permitem identificar usuários, separar os restaurantes, aplicar permissões,
        registrar o histórico de alterações e apresentar os relatórios solicitados.
        O endereço IP é utilizado nos limites de tentativas de acesso e cadastro.</p>
    </section>
    <section>
      <h2>Quando você entra com Google</h2>
      <p>Recebemos do Google o nome, o e-mail, a indicação de endereço verificado e um identificador da conta.
        Usamos esses dados para identificar o usuário, criar ou vincular seu acesso e confirmar o endereço quando aplicável.</p>
      <p>O login solicita somente identificação, e-mail e perfil básico. Não solicita acesso ao Gmail,
        aos contatos ou aos arquivos do usuário. O EstoQ não recebe sua senha do Google.
        Os tokens do Google não são guardados no banco nem no armazenamento do navegador.</p>
      <p>Se o e-mail já pertence a uma conta EstoQ, o vínculo exige a senha atual dessa conta.
        O identificador Google é mantido no cadastro para os próximos acessos.</p>
    </section>
    <section>
      <h2>Confirmação de e-mail e senhas</h2>
      <p>Quando a confirmação por link está habilitada, o endereço é usado para enviar mensagens de verificação.
        O envio pela Gmail API é autorizado somente pela conta remetente do EstoQ; não dá acesso à caixa postal dos clientes.</p>
      <p>Senhas locais são armazenadas como hashes BCrypt. Contas criadas somente com Google não recebem uma senha local automática.
        Links de confirmação têm validade limitada e só podem ser utilizados uma vez.</p>
    </section>
    <section>
      <h2>Hospedagem e compartilhamento</h2>
      <p>A aplicação utiliza Vercel para o frontend, Render para o backend e Supabase para o banco de dados.
        Esses provedores processam os dados necessários à hospedagem. O Google participa da autenticação
        e do envio de confirmações, conforme a função utilizada.</p>
      <p>Dados recebidos do Google são usados para o acesso descrito nesta política.
        Não são usados para publicidade, vendidos ou enviados a modelos de inteligência artificial.</p>
    </section>
    <section>
      <h2>Cookies e histórico</h2>
      <p>O EstoQ usa um cookie de sessão para manter o acesso e mecanismos de proteção das operações.
        O cache da interface é separado por usuário e restaurante e é reiniciado na troca de sessão.
        A autenticação no site do Google segue também a <a href="https://policies.google.com/privacy">política do Google</a>.</p>
      <p>Desativar um usuário impede seu acesso e preserva o histórico operacional.
        A aplicação mantém os registros da cozinha para consulta e rastreabilidade.</p>
    </section>
    <section>
      <h2>Solicitações sobre seus dados</h2>
      <p>Para solicitar acesso, correção, exclusão ou esclarecer dúvidas sobre seus dados pessoais,
        entre em contato por <a href="mailto:estoq.sistema@gmail.com">estoq.sistema@gmail.com</a> e informe o e-mail do cadastro.
        Não envie senhas, tokens ou dados de cartão.</p>
      <p>Você também pode remover a autorização do EstoQ nas configurações da sua conta Google.
        Isso é diferente de solicitar a exclusão dos registros já cadastrados no EstoQ.</p>
    </section>
  </PaginaPublica>;
}
