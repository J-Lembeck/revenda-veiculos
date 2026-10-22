# Revenda de veículos

API de uma plataforma de revenda de veículos: cadastro e edição de veículos, cadastro de compradores,
vitrine ordenada por preço e o processo de compra de ponta a ponta — reservar o veículo, receber o
código de pagamento, pagar, retirar e dar baixa no estoque. Reserva concorrente, pagamento não feito e
desistência são tratados por uma **SAGA orquestrada**; o dado pessoal do comprador fica cifrado e
isolado num único serviço.

> **Estado:** fundação do repositório. Os quatro serviços sobem e respondem `health`, o ambiente local
> sobe por Docker Compose e o CI roda o build. Os casos de uso entram nas próximas etapas.

## Serviços

| Módulo | Porta | Responsável por |
|---|---|---|
| `common` | — | biblioteca de infraestrutura compartilhada (sem regra de negócio) |
| `vehicles-service` | 8081 | estoque: cadastro, edição, vitrine, reserva condicional, baixa |
| `customers-service` | 8082 | cadastro do comprador; único dono do dado pessoal, cifrado em repouso |
| `sales-service` | 8083 | compra e orquestração da saga, webhook de pagamento, prazos, retirada |
| `payment-gateway-mock` | 8084 | gateway de pagamento simulado: cobrança, Pix sintético, estorno, webhook |

Cada serviço sobe sozinho, tem o próprio banco e fala com os outros só por HTTP, fila ou webhook — nenhum
lê o banco de outro. No CI, cada um é compilado e testado num job próprio. O pacote base é
`com.revenda`.

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL 16 · Maven multi-módulo (com *wrapper*). No ambiente local,
Docker Compose com PostgreSQL, LocalStack (SQS, KMS, S3, Secrets Manager) e Keycloak (OIDC).

## Pré-requisitos

- JDK 21, com `JAVA_HOME` apontando para ele.
- Docker (no Windows e no macOS, o Docker Desktop aberto).
- Maven não precisa estar instalado: use o `./mvnw` (ou `mvnw.cmd` no Windows).

## Como subir

### 1. Ambiente local

```bash
cd infra
cp .env.example .env
docker compose up -d
docker compose ps
```

Os três contêineres devem aparecer como `healthy` (o Keycloak leva cerca de um minuto na primeira vez).
Todas as portas escutam só em `127.0.0.1`.

| Contêiner | Porta | O que sobe |
|---|---|---|
| `postgres` | 5440 | bancos `vehicles`, `customers` e `sales`; para cada um, a *role* `<serviço>_owner` (dona do banco, roda as migrations) e a `<serviço>_app` (só conecta; a aplicação usa esta) |
| `localstack` | 4566 | fila `vehicle-commands.fifo` com DLQ (5 recebimentos), chaves KMS `alias/customers-data` e `alias/documents`, bucket `transfer-documents` (SSE-KMS, versionado, sem acesso público) e os segredos `revenda/payment-gateway-api-key` e `revenda/payment-webhook-secret` |
| `keycloak` | 8180 | realm `revenda`: papéis `CUSTOMER`, `SELLER` e `ADMIN`, clientes `revenda-web`, `sales-service` e `customers-service`, usuários sintéticos |

O `.env` é git-ignorado e só tem valores de desenvolvimento. Para recomeçar do zero (apaga os bancos
locais): `docker compose down -v`.

### 2. Build e testes

```bash
./mvnw -B verify
```

### 3. Rodar um serviço

```bash
./mvnw -B install -DskipTests
./mvnw -pl vehicles-service spring-boot:run
```

```bash
curl http://localhost:8081/actuator/health
```

Resposta esperada: `{"groups":["liveness","readiness"],"status":"UP"}`. Só o `health` fica exposto;
os demais endpoints do Actuator respondem `404`.

## Usuários sintéticos

Os usuários de teste e as senhas de desenvolvimento estão em
[`infra/keycloak/realm.json`](infra/keycloak/realm.json) — todos com e-mail `@example.test`:

| Usuário | Papel | Para testar |
|---|---|---|
| `ana.cliente@example.test` | `CUSTOMER` | compra com cadastro completo |
| `bruno.sem-cadastro@example.test` | `CUSTOMER` | compra sem cadastro (deve ser recusada) |
| `caio.menor@example.test` | `CUSTOMER` | comprador menor de idade (deve ser recusado) |
| `vera.vendedora@example.test` | `SELLER` | cadastro de veículos e confirmação da retirada |
| `otto.admin@example.test` | `ADMIN` | revelação de CPF com motivo e pedidos de titular |

`SELLER` e `ADMIN` configuram o segundo fator (TOTP, em qualquer app autenticador) no primeiro login.
Quem se cadastra pela tela do Keycloak recebe o papel `CUSTOMER`. Os segredos dos clientes de serviço
vêm do `.env`.

## Estrutura

```
revenda-veiculos/
├── pom.xml                  POM pai: versões, plugins e módulos
├── common/                  infraestrutura compartilhada (+ base de teste no test-jar)
├── vehicles-service/
├── customers-service/
├── sales-service/
├── payment-gateway-mock/
├── infra/
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── postgres/init.sh     bancos e roles
│   ├── localstack/init.sh   fila, chaves, bucket e segredos
│   └── keycloak/realm.json  realm, clientes, papéis e usuários sintéticos
└── .github/
    ├── workflows/ci.yml     um job por serviço
    └── scripts/affected-modules.sh
```

## Versões fixas das imagens

Toda imagem do Compose tem versão fixa. O LocalStack está na **4.14.0**: a partir de março de 2026 as
imagens novas exigem token de conta, e a 4.14.0 é a última que sobe sem cadastro — assim qualquer pessoa
que clona o repositório roda tudo sem criar conta em serviço externo. Na nuvem os serviços são os da AWS.

## CI

GitHub Actions com **um job por serviço**: cada job compila e testa só o seu serviço e o `common`
(`./mvnw -B verify -pl <serviço> -am`), num runner próprio, com JDK 21 (Temurin) e cache do Maven. A
falha de um serviço não interrompe os outros.

- Em *pull request*, só rodam os serviços cuja pasta mudou. Mudança no `common`, no `pom.xml` raiz, no
  wrapper ou em `.github/` roda todos; mudança só em `infra/` ou na documentação não roda nenhum. A
  escolha fica em [`.github/scripts/affected-modules.sh`](.github/scripts/affected-modules.sh).
- Em *push* na `main`, rodam todos.
- O job `resultado` resume os demais e é o *check* a exigir na proteção da `main` — serviço que não
  precisou rodar conta como aprovado.
- Os relatórios de teste de cada serviço ficam como artefato por 14 dias.
