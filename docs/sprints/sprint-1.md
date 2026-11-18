# Sprint 1: Veridt

| | |
|---|---|
| **Período** | 06/11/2026 a 18/11/2026 |
| **Branch de integração** | `desenv` (merge `b353089`) |
| **Pull requests** | #1, #2, #4, #6, #7, #9, #11, #13, #14, #17 e #18 |
| **Requisitos implementados** | 6 de 16 (37,5%): REQ 01, 02, 03, 04, 05 e 07 |
| **Requisito parcial** | REQ 06 (pagamento simulado, sem Mercado Pago) |
| **Tarefas do backlog concluídas** | 20 de 42 |

## Objetivo

Implementar ao menos 30% dos requisitos do Veridt seguindo a arquitetura definida nas ADRs. A sprint entregou a base técnica do back-end e os fluxos completos de conta de usuário e de compra de créditos, além do armazenamento de arquivos que servirá à captura de conteúdo.

## Requisitos implementados

O documento de requisitos tem 16 requisitos, porque o número REQ 11 aparece duas vezes: concluir a captura de tela (**REQ 11a**) e confirmar a captura por e-mail (**REQ 11b**).

| Requisito | Situação | Como foi atendido |
|---|---|---|
| REQ 01: Cadastrar usuário | Implementado | Cadastro de advogados e usuários comuns com os campos do Apêndice A (nome, e-mail, senha, CPF e OAB para advogados), conforme a decisão D1. Valida CPF, política de senha e unicidade de e-mail e CPF. A conta é ativada por confirmação de e-mail. |
| REQ 02: Recuperar senha | Implementado | Link de redefinição enviado por e-mail, com token de uso único válido por 15 minutos. A resposta não revela se o e-mail existe. |
| REQ 03: Logar no sistema | Implementado | Login por e-mail e senha, com sessão no servidor. Credenciais inválidas não revelam se o e-mail existe. |
| REQ 04: Sair do sistema | Implementado | Logout encerra a sessão no servidor. |
| REQ 05: Comprar créditos | Implementado | Seleção de pacote (básico, médio e premium, conforme D2), dados de faturamento do Apêndice A e criação do pedido. O valor é sempre calculado no servidor. |
| REQ 06: Efetuar pagamento | **Parcial** | O pagamento passa por uma interface `PaymentGateway` com implementação simulada, e a confirmação já concede os créditos uma única vez. Faltam a integração com o Mercado Pago, o Pix e o webhook. |
| REQ 07: Confirmar pagamento por e-mail | Implementado | Quando o pagamento é confirmado, o usuário recebe o e-mail de compra confirmada no endereço de faturamento. |
| REQ 08 a REQ 15 | Não iniciados | Captura, gravação e registros ficam para a próxima sprint. Os modelos de e-mail de REQ 10 e REQ 11b e o armazenamento de arquivos já estão prontos. |

## ADRs implementadas

### ADR-0001: Monólito modular
A aplicação é um único artefato Spring Boot, organizado em módulos com o Spring Modulith. O teste `ModularityTests` verifica a cada execução que nenhum módulo acessa outro fora das dependências permitidas:

| Módulo | Pode depender de |
|---|---|
| `identity` | `notification` |
| `credits` | `notification` |
| `records` | `storage` |
| `capture` | `credits`, `records`, `notification`, `storage` |
| `notification` e `storage` | Nenhum |

Os módulos se comunicam por APIs públicas e eventos, o que mantém aberta a migração para serviços distribuídos prevista na ADR.

### ADR-0002: Top 4 características arquiteturais

| Característica | Implementação |
|---|---|
| Segurança | Acesso negado por padrão, senhas em Argon2id, sessões no servidor, proteção CSRF, confirmação de e-mail e tokens guardados apenas como hash SHA-256. Cada usuário acessa somente os próprios pedidos e saldo. Dados pessoais nunca aparecem em logs. |
| Auditabilidade | Tabela `auditoria` somente de inserção: triggers no banco recusam `UPDATE`, `DELETE` e `TRUNCATE`. Cada arquivo armazenado tem SHA-256, calculado durante a gravação e salvo também no servidor de arquivos. |
| Confiabilidade | E-mail assíncrono com reenvio após falha. A confirmação de pagamento é idempotente e concede créditos uma única vez, em uma transação. O saldo nunca fica negativo, mesmo com débitos simultâneos. Arquivos não podem ser sobrescritos. |
| Rastreabilidade | Cadastro, login, logout, redefinição de senha, pedidos, pagamentos e concessão de créditos são registrados com usuário, data, operação e resultado. As movimentações de crédito também são somente de inserção e ligadas ao pedido de origem. |

### ADR-0003: Particionamento por domínio
Os pacotes seguem os domínios do sistema, e não camadas técnicas: `identity`, `credits`, `capture`, `records`, `notification` e `storage`. As camadas (web, serviço, repositório) ficam dentro de cada módulo, no subpacote `internal`.

## Desvios da arquitetura

| # | Documentação | Implementação | Motivo |
|---|---|---|---|
| 1 | A auditoria faz parte do `RecordsService` ("Metadados e auditoria"). | A auditoria fica em `shared.audit`, fora dos módulos, e é usada por todos. | Pelas dependências do diagrama de pacotes, `identity` e `credits` não podem acessar `records`, mas também precisam registrar auditoria. |
| 2 | Não existe código compartilhado entre módulos. | O pacote `shared` reúne auditoria, tratamento de erros e validações comuns (CPF e UF). | Evita duplicar regras transversais em cada módulo sem criar dependências proibidas. |
| 3 | `NotificationService` e `FileStorageService` ficam juntos no pacote "Externo". | Viraram dois módulos separados, `notification` e `storage`. | São responsabilidades independentes, sem dependência entre si. |
| 4 | O cadastro não exige confirmação de e-mail. | Contas só fazem login após confirmar o e-mail, e contas não confirmadas em 7 dias são removidas. | Reforça a característica Segurança e garante que os e-mails de pagamento e de conclusão cheguem ao dono da conta. |

## Decisões técnicas da sprint

Pontos que a documentação de arquitetura deixava em aberto:

| Tema | Decisão |
|---|---|
| Frontend | SPA separada, consumindo a API REST. O Docker Compose já sobe o frontend. |
| Armazenamento de arquivos | API S3 genérica, com SeaweedFS no Docker Compose. Trocar de provedor exige só configuração. |
| Pacotes de créditos | Valores provisórios: Básico (10 créditos a R$ 5,00), Médio (50 a R$ 4,50) e Premium (100 a R$ 4,00). Mudanças entram por nova migration, e pedidos antigos guardam uma cópia do pacote. |
| Pagamento | Até a integração com o Mercado Pago, um gateway simulado. A rota de simulação só existe quando habilitada por configuração e vem desligada por padrão. |

## Princípios SOLID

| Princípio | Onde | Por quê |
|---|---|---|
| Responsabilidade única (S) | Controllers só recebem e delegam, serviços concentram as regras e repositórios só persistem. Exemplos: `RegistrationConstraintTranslator` só traduz erros do banco, e `EmailTemplateRenderer` só monta o e-mail. | Cada classe muda por um único motivo, o que facilita testes e manutenção. |
| Aberto/fechado (O) | `AuditOperation`: cada módulo cria seu próprio enum (`IdentityAuditOperation`, `CreditsAuditOperation`). `EmailType` define assunto e parâmetros de cada e-mail. | Novas operações de auditoria e novos tipos de e-mail entram sem alterar o código existente. |
| Substituição de Liskov (L) | `FileStorageContractTests` roda os mesmos testes em `S3FileStorage` e na versão em memória. | Garante que qualquer implementação de `FileStorage` funcione no lugar de outra. |
| Segregação de interfaces (I) | `FileStorage` tem três métodos (`store`, `open`, `find`), `CreditLedger` tem três (`balance`, `credit`, `debit`) e `PaymentGateway` tem um (`start`). | Os módulos dependem só do que usam. `capture`, por exemplo, verá apenas o saldo e o débito. |
| Inversão de dependência (D) | Serviços dependem de interfaces: `EmailSender` (SMTP), `FileStorage` (S3), `PaymentGateway` (simulado, futuramente Mercado Pago) e `CreditLedger` (JDBC). | Serviços externos podem ser trocados ou simulados nos testes sem alterar as regras de negócio. |

## Entregas por módulo

### Fundação (PR #1)
- Estrutura dos seis módulos e verificação automática das dependências.
- Docker Compose com Postgres e Mailpit, `.env.example` e `.dockerignore`.
- Tratamento global de erros, com resposta padronizada e exceções de negócio (402, 404, 409 e 422).

### Notification (PR #2)
- E-mail assíncrono pelo evento `EmailRequested`. O envio só acontece após o commit de quem pediu, e uma falha não desfaz a operação principal.
- Modelos HTML de confirmação de e-mail, recuperação de senha, compra confirmada, gravação concluída e captura concluída.
- E-mails com tokens são apagados do banco após o envio.

### Identity (PRs #4, #6, #7, #13 e #17)
- Cadastro, login, logout, recuperação de senha e confirmação de e-mail.
- Advogados podem ter várias inscrições na OAB, com pelo menos uma obrigatória.
- Rota `GET /api/me` para o frontend saber quem está logado.
- Auditoria de todas as operações de conta.

### Storage (PRs #9 e #11)
- API `FileStorage` com SHA-256 calculado na gravação e proibição de sobrescrita.
- Implementação S3 sobre o SeaweedFS e versão em memória para testes.

### Credits (PR #18)
- Pacotes de créditos, saldo e movimentações somente de inserção, com chave de idempotência.
- Pedido com dados de faturamento, cópia do pacote e cabeçalho `Idempotency-Key`.
- Confirmação de pagamento idempotente, com concessão de créditos e e-mail.
- Auditoria de pedidos, pagamentos e créditos concedidos.

### Infraestrutura (PR #14)
- Serviço do frontend no Docker Compose.

## API REST

| Método | Rota | Acesso | Função |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | Público | Gera o token CSRF |
| `POST` | `/api/auth/register` | Público | Cadastro |
| `POST` | `/api/auth/email-confirmation/confirm` | Público | Confirma o e-mail |
| `POST` | `/api/auth/email-confirmation/resend` | Público | Reenvia a confirmação |
| `POST` | `/api/auth/login` | Público | Login |
| `POST` | `/api/auth/logout` | Autenticado | Logout |
| `POST` | `/api/auth/password-reset` | Público | Solicita a recuperação de senha |
| `POST` | `/api/auth/password-reset/confirm` | Público | Redefine a senha |
| `GET` | `/api/me` | Autenticado | Dados do usuário logado |
| `GET` | `/api/credits/packages` | Autenticado | Lista os pacotes |
| `GET` | `/api/credits/balance` | Autenticado | Consulta o saldo |
| `POST` | `/api/credits/purchases` | Autenticado | Cria um pedido de compra |
| `GET` | `/api/credits/payments` | Autenticado | Lista os próprios pedidos |
| `GET` | `/api/credits/payments/{id}` | Autenticado | Consulta um pedido |
| `POST` | `/api/credits/payments/{id}/simulation` | Autenticado, só com simulação habilitada | Simula o resultado do pagamento |

## Banco de dados

| Migration | Conteúdo |
|---|---|
| `V1__infraestrutura_spring` | Eventos do Spring Modulith e sessões do Spring Session |
| `V2__identity` | Usuários e tokens de recuperação de senha |
| `V3__inscricao_oab` | Inscrições na OAB e obrigatoriedade para advogados |
| `V4__auditoria` | Auditoria somente de inserção |
| `V5__confirmacao_email` | Tokens de confirmação de e-mail |
| `V6__creditos` | Pacotes, saldo, movimentações somente de inserção e pedidos |

## Qualidade

- **Testes:** testes automatizados de unidade e de integração. Os de integração usam Postgres, Mailpit e SeaweedFS reais via Testcontainers.
- **Cobertura:** fronteiras entre módulos, tratamento de erros, e-mail, cadastro, segurança e CSRF, rotas REST, auditoria, armazenamento e créditos, incluindo débitos concorrentes e confirmações repetidas.
- **Execução:** o README traz as instruções para subir o sistema com Docker Compose (Postgres, Mailpit, SeaweedFS, back-end e frontend).
