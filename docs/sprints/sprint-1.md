# Sprint 1: Fundação, notificações, identidade e armazenamento

| | |
|---|---|
| **Período** | 06/11/2026 a 11/11/2026 |
| **Branch de integração** | `desenv` (merge `989c2ec`) |
| **Pull requests** | #1, #2, #4, #6, #7, #9 e #11 |
| **Requisitos atendidos** | REQ 01, 02, 03 e 04 |
| **Tarefas concluídas** | 14 de 41 do backlog |

## Objetivo

Preparar a base técnica do back-end e entregar o primeiro módulo de negócio completo: cadastro, login, logout e recuperação de senha, com e-mail assíncrono e auditoria. A sprint também entregou o armazenamento de arquivos, base para os módulos de captura e de registros.

## Entregas

### Fundação (PR #1)

| Tarefa | Entrega |
|---|---|
| FND-01 | Seis módulos Spring Modulith (`identity`, `credits`, `capture`, `records`, `notification`, `storage`), com as dependências permitidas declaradas e verificadas pelo `ModularityTests`. |
| FND-02 | Ambiente de desenvolvimento com Postgres e Mailpit no Docker Compose, `.env.example` e `.dockerignore`. |
| FND-03 | Tratamento global de erros em `shared.error`, com resposta padronizada (`ErrorResponse`) e exceções de negócio (422, 404 e 409). |

Antes do PR, foram corrigidos erros que impediam a execução: sintaxe das variáveis no `compose.yaml`, porta do Postgres e carregamento do `.env`.

### Notification (PR #2)

| Tarefa | Entrega |
|---|---|
| NOT-01 | Envio assíncrono por meio do evento `EmailRequested`. O e-mail só sai após o commit de quem o pediu, uma falha no envio não desfaz a operação principal e eventos pendentes são reenviados ao reiniciar. |
| NOT-02 | Modelos HTML para recuperação de senha, compra confirmada, gravação concluída e captura concluída, na paleta do Figma. |

O envio roda em um executor próprio, para não ocupar as threads do restante da aplicação, e os logs não registram destinatário nem parâmetros.

### Identity (PRs #4 e #6)

| Tarefa | Entrega |
|---|---|
| ID-01 | Spring Security que nega tudo por padrão, senhas em Argon2id e sessões no banco (Spring Session JDBC). |
| ID-02 | Usuários dos tipos `ADVOGADO` e `COMUM`, com e-mail e CPF únicos. Advogados podem ter várias inscrições na OAB, com pelo menos uma obrigatória. |
| ID-03 | Cadastro com validação dos campos, dos dígitos do CPF e da política de senha. |
| ID-04 | Login por e-mail. Credenciais inválidas não revelam se o e-mail existe, e a sessão é trocada no login e encerrada no logout. |
| ID-05 | Recuperação de senha com token de uso único e validade de 15 minutos. Só o hash fica no banco, e o tempo de resposta não revela se o e-mail existe. Redefinir a senha encerra todas as sessões do usuário. |
| ID-06 | Rotas REST para o frontend SPA, com proteção CSRF. |

**Rotas disponíveis**

| Método | Rota | Acesso | Função |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | Público | Gera o token CSRF |
| `POST` | `/api/auth/register` | Público | Cadastro (201) |
| `POST` | `/api/auth/login` | Público | Login (204) |
| `POST` | `/api/auth/logout` | Autenticado | Logout |
| `POST` | `/api/auth/password-reset` | Público | Solicita a recuperação |
| `POST` | `/api/auth/password-reset/confirm` | Público | Redefine a senha |

### Auditoria (PR #7)

| Tarefa | Entrega |
|---|---|
| ID-07 | Log de auditoria em `shared.audit`, usado por todos os módulos. A tabela `auditoria` é somente de inserção: triggers no banco recusam `UPDATE`, `DELETE` e `TRUNCATE`. O módulo `identity` registra cadastro, login, logout e redefinição de senha, com sucesso ou falha. |

Falhas são registradas em transação própria, para não se perderem quando a operação principal é desfeita. Nenhum dado pessoal vai para o detalhe do registro.

### Storage (PRs #9 e #11)

| Tarefa | Entrega |
|---|---|
| STO-01 | API pública `FileStorage` para gravar, abrir e consultar arquivos. Cada gravação retorna chave, tamanho e SHA-256, calculado durante a escrita. Um arquivo nunca é sobrescrito, e as chaves são validadas para impedir caminhos inválidos. Inclui testes de contrato que toda implementação deve passar e uma versão em memória para os testes de outros módulos. |
| STO-02 | Implementação real sobre a API S3 (AWS SDK v2), usando o SeaweedFS no Docker Compose, com volume próprio. O próprio servidor recusa sobrescrita, e o hash fica salvo nos metadados do arquivo. O bucket é criado na primeira gravação, então a aplicação sobe mesmo sem o S3 disponível. |

O SeaweedFS foi escolhido porque a versão comunitária do MinIO foi descontinuada. Como a aplicação usa a API S3 genérica, trocar de provedor exige só configuração.

## Banco de dados

| Migration | Conteúdo |
|---|---|
| `V1__infraestrutura_spring` | Tabelas de eventos do Spring Modulith e de sessões do Spring Session |
| `V2__identity` | `usuario` e `token_recuperacao_senha` |
| `V3__inscricao_oab` | `inscricao_oab` e verificação de inscrição obrigatória para advogados |
| `V4__auditoria` | `auditoria` e triggers que impedem alterações |

## Qualidade

- **Testes:** 159 testes automatizados, todos passando em 11/11/2026 no código do `desenv`. Os testes de integração usam Postgres, Mailpit e SeaweedFS reais via Testcontainers.
- **Cobertura por área:** fronteiras entre módulos, tratamento de erros, envio e modelos de e-mail, regras de cadastro (CPF, senha, restrições do banco), segurança e CSRF, rotas REST, auditoria e armazenamento de arquivos.
- **Características da ADR-0002 atendidas:**
  - Segurança: Argon2id, sessões no servidor, CSRF e acesso negado por padrão.
  - Confiabilidade: e-mail assíncrono com reenvio e arquivos que não podem ser sobrescritos.
  - Rastreabilidade e auditabilidade: log de auditoria que não pode ser alterado e SHA-256 de cada arquivo armazenado.

## Decisões tomadas na sprint

| Pendência | Decisão | Situação |
|---|---|---|
| P1: Frontend | SPA | Aplicada nas rotas REST e no CSRF |
| P3: Armazenamento | API S3 com SeaweedFS | Aplicada no módulo `storage` |
| P6: Auditoria fora de `records` | `shared.audit`, uma tabela usada por todos os módulos | Aplicada na auditoria do `identity` |
