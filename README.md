# Veridt

Back-end do Veridt, uma plataforma web para capturar e gravar conteúdo de páginas da internet, produzindo evidências digitais rastreáveis e verificáveis.

Projeto do componente curricular Engenharia de Software I (MATA62).

- **Instituição:** Universidade Federal da Bahia (UFBA), Instituto de Computação
- **Semestre:** 2026.2
- **Professor:** Eduardo Santana de Almeida
- **Discentes:** Igor Reis Santos, Edwu Morais Braga, Ana Beatriz Figueiredo de Aquino e Beatriz Mendes Rios

## Sobre o sistema

Advogados e usuários comuns compram pacotes de créditos (básico, médio e premium) pelo Mercado Pago ou via Pix. Os créditos são usados para registrar capturas de tela ou vídeos de navegação de um site. Depois, o usuário pode consultar os registros, gerar relatórios e exportar os arquivos em ZIP. Compras e registros concluídos são confirmados por e-mail.

## Status

Em desenvolvimento. Concluído na Sprint 1 (detalhes em [docs/sprints/sprint-1.md](docs/sprints/sprint-1.md)):

- **Identidade (REQ 01-04):** cadastro de advogados e usuários comuns, login, logout, recuperação de senha e auditoria.
- **Notificação:** envio assíncrono e confiável de e-mails.
- **Armazenamento:** gravação de arquivos com SHA-256 e proteção contra sobrescrita.

Créditos, captura e registros ainda serão implementados.

## Stack

| Área | Tecnologias |
|---|---|
| Linguagem e framework | Java 25, Spring Boot 4.1.2 |
| Arquitetura | Spring Modulith 2.1.2 |
| Banco de dados | PostgreSQL 16, Spring Data JPA, Flyway |
| Segurança | Spring Security, Spring Session JDBC, Argon2id |
| Armazenamento | API S3 (AWS SDK v2) com SeaweedFS |
| Comunicação | Spring Web MVC, WebSocket, Spring Mail |
| Testes | JUnit, Testcontainers |
| Infraestrutura | Docker, Docker Compose, Maven Wrapper |

## Arquitetura

O sistema é um monólito modular, particionado por domínio. As fronteiras entre módulos são verificadas automaticamente em teste.

| Módulo | Responsabilidade | Requisitos | Situação |
|---|---|---|---|
| `identity` | Cadastro, login, logout e recuperação de senha | REQ 01-04 | Pronto |
| `notification` | Envio assíncrono de e-mails | REQ 02, 07, 10 e 11 | Pronto |
| `storage` | Armazenamento e integridade dos arquivos | Suporte a `capture` e `records` | Pronto |
| `credits` | Pacotes, saldo e pagamentos | REQ 05-07 | A fazer |
| `capture` | Captura de tela e gravação de navegação | REQ 08-11 | A fazer |
| `records` | Listagem, detalhes, relatórios e exportação em ZIP | REQ 12-15 | A fazer |

Dependências permitidas entre módulos: `identity` → `notification`, `credits` → `notification`, `records` → `storage` e `capture` → `credits`, `records`, `notification`, `storage`. O pacote `shared` não é um módulo: guarda o tratamento de erros e a auditoria, usados por todos.

As características prioritárias são segurança, auditabilidade, confiabilidade e rastreabilidade.

## Como executar

**Pré-requisito:** Docker com Docker Compose. Não é preciso instalar Java: o `Dockerfile` compila a aplicação dentro do contêiner.

1. Copie o `.env.example` para `.env` na raiz do projeto e defina `POSTGRES_PASSWORD` e `S3_SECRET_KEY`. O `.env` não deve ser commitado.

   ```bash
   cp .env.example .env
   ```

2. Clone o [repositório do frontend](https://github.com/RottyWheels/-MATA62---ENGENHARIA-DE-SOFTWARE-I-frontend) **ao lado** deste. Se ele estiver em outra pasta, defina o caminho em `FRONTEND_PATH` no `.env`.

3. Suba tudo:

   ```bash
   docker compose up --build
   ```

| Serviço | Endereço |
|---|---|
| Frontend (SPA) | `http://localhost:3000` |
| API | `http://localhost:8080` |
| PostgreSQL | `localhost:5433` |
| Mailpit (painel de e-mails) | `http://localhost:8025` |
| Mailpit (SMTP) | `localhost:1025` |
| SeaweedFS (API S3) | `http://localhost:8333` |

O Mailpit captura os e-mails enviados em desenvolvimento, sem entregá-los de verdade. O frontend encaminha `/api` e `/ws` para o backend, então o navegador fala com uma única origem e o cookie de sessão funciona sem CORS.

Para encerrar:

```bash
docker compose down
```

### Variáveis de ambiente

Só as do PostgreSQL são obrigatórias. As demais têm um padrão de desenvolvimento, definido no `compose.yaml` e no `application.properties`, e devem ser definidas em produção.

| Variável | Padrão | Uso |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Obrigatórias | Banco de dados |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | `veridt`, `veridt-dev-secret` | Credenciais do armazenamento. **Troque o segredo:** o padrão é público |
| `S3_BUCKET` | `veridt-arquivos` | Bucket dos arquivos, criado na primeira gravação |
| `FRONTEND_PATH` | `../-MATA62---ENGENHARIA-DE-SOFTWARE-I-frontend` | Pasta do repositório do frontend |
| `FRONTEND_PORT` | `3000` | Porta do frontend |
| `RESET_URL` | `http://localhost:3000/redefinir-senha` | Página do frontend que recebe o link de redefinição de senha. Com `pnpm dev`, use a porta `5173` |
| `RESET_TOKEN_VALIDITY` | `15m` | Validade do link de redefinição |
| `PASSWORD_MIN_LENGTH` | `8` | Tamanho mínimo da senha |
| `MAIL_FROM` | `no-reply@veridt.local` | Remetente dos e-mails |
| `MAIL_THREADS` | `2` | Envios de e-mail simultâneos |
| `MAIL_SENSITIVE_CLEANUP_INTERVAL` | `PT5M` | Intervalo da limpeza dos e-mails com token já enviados |

### Sem o Docker para a aplicação

Com JDK 25 instalado, `./mvnw spring-boot:run` roda a aplicação na máquina e sobe pelo Compose só o PostgreSQL, o Mailpit e o SeaweedFS.

## API

Todas as rotas exigem login, exceto as marcadas como públicas. Erros seguem um formato padrão, com `status`, `code`, `message` e `errors` (campos inválidos).

| Método e rota | Acesso | Descrição |
|---|---|---|
| `GET /api/auth/csrf` | Público | Grava o cookie `XSRF-TOKEN` |
| `POST /api/auth/register` | Público | Cadastro de usuário comum ou advogado, com inscrições na OAB |
| `POST /api/auth/login` | Público | Login com `email` e `password` em JSON; cria o cookie `SESSION` |
| `POST /api/auth/logout` | Logado | Encerra a sessão |
| `GET /api/me` | Logado | Dados do usuário logado |
| `POST /api/auth/password-reset` | Público | Pede o link de redefinição; responde 202 com e-mail cadastrado ou não |
| `POST /api/auth/password-reset/confirm` | Público | Define a nova senha com o `token` do link |
| `GET /actuator/health` | Público | Saúde da aplicação |

**CSRF:** todo `POST` precisa do cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`. Chame `GET /api/auth/csrf` antes do primeiro `POST` e de novo depois do login, porque o token muda. Bibliotecas como o Axios fazem isso automaticamente.

## Testes

Os testes exigem JDK 25, com `JAVA_HOME` configurado, e Docker em execução: o PostgreSQL, o Mailpit e o SeaweedFS de teste sobem pelo Testcontainers.

No Windows:

```bash
.\mvnw test
```

No Linux ou macOS:

```bash
./mvnw test
```

Para rodar uma classe só: `./mvnw test -Dtest=NomeDaClasse`.

## Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `role "..." does not exist` ao subir | O PostgreSQL só aplica `POSTGRES_USER`, `POSTGRES_DB` e `POSTGRES_PASSWORD` a um volume vazio. Se você os alterou depois do primeiro `up`, rode `docker compose down -v`, que **apaga os dados do banco**, e suba de novo |
| Erro de build do `frontend` | O repositório do frontend não está ao lado deste. Clone-o ou defina `FRONTEND_PATH` no `.env` |
| 502 nas chamadas à API logo após o `up` | O backend leva cerca de 30 segundos para iniciar; as páginas do frontend já respondem antes disso |
| `port is already allocated` | Outro processo usa a porta. Rode `docker compose down` e suba de novo, ou mude a porta no `.env` |
| Testes falham com timeout do Testcontainers | Pouca memória para os contêineres de teste. Pare a stack (`docker compose stop`) antes de rodar a suíte |

## Estrutura do repositório

```
.
├── .mvn/                         # Maven Wrapper
├── docs/sprints/                 # Documentação das sprints
├── src/
│   ├── main/
│   │   ├── java/br/ufba/veridt/  # Um pacote por módulo, mais o shared
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/migration/     # Migrations do Flyway
│   │       └── templates/email/  # Modelos HTML dos e-mails
│   └── test/java/br/ufba/veridt/
├── .env.example
├── compose.yaml
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

## Documentação

Requisitos, ADRs, diagramas e táticas arquiteturais estão no repositório [MATA62-Veridt](https://github.com/iuryyxd/MATA62-Veridt). As telas estão no [Figma do Veridt](https://www.figma.com/design/D4vDhuk1DBAJY9knexl7Hs/Veridt?node-id=0-1&t=YgLGf4RsxAjWx53e-1). O andamento de cada sprint está em [docs/sprints](docs/sprints).
