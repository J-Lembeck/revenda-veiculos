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
| `vehicles-service` | 8081 | estoque: cadastro, edição, vitrine, reserva condicional, comandos da fila (liberar, vender, dar baixa) |
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

Os testes que usam banco sobem um PostgreSQL próprio pelo Testcontainers: o Docker precisa estar aberto,
mas o Compose não precisa estar de pé. Esse PostgreSQL é preparado pelo mesmo `infra/postgres/init.sh` do
ambiente local, com senhas sorteadas a cada execução: o teste de um serviço roda as migrations com a role
`<serviço>_owner` e conecta a aplicação com a `<serviço>_app`, como na subida real.

A base de teste fica no test-jar do `common` (`com.revenda.common.test`):

| Peça | Para quê |
|---|---|
| `PostgresTestDatabase` | PostgreSQL único da suíte; `register(registry, "vehicles")` liga o *datasource* à `_app` e o Flyway à `_owner` |
| `LocalStackTestContainer`, `LocalStackTestKms`, `LocalStackTestSqs` | LocalStack único da suíte, com o KMS e o SQS; `createFifoQueue` cria uma fila FIFO própria do teste, com DLQ |
| `TestTokens`, `TestTokenConfiguration` | tokens de pessoa (por papel) e de serviço, assinados por uma chave gerada na memória |
| `ApiDriver` | chama a API em JSON com o token de quem chama e uma `Idempotency-Key` nova em todo `POST` |
| `SyntheticData` | CPF sorteado com dígito verificador válido, e-mail em `@example.test`, placa, chassi e RENAVAM válidos — só dado inventado |
| `MovableClock` | relógio que o teste adianta |
| `ServiceFoundationContract`, `ResourceServerContract` | testes que todo serviço herda: `health`, formato de erro, log mascarado, token |
| `ServiceArchitectureRules` | regras de arquitetura que todo serviço roda no `<Serviço>ArchitectureTests` (abaixo) |

#### Regras de arquitetura

Cada serviço segue a arquitetura hexagonal — `domain/{model,service,ports/in,ports/out}`,
`application/service` e `infrastructure/{adapters/in,adapters/out,config}` — e o ArchUnit confere no
código compilado, no `./mvnw -B verify`:

| Regra | O que recusa |
|---|---|
| `domainIsPlainJava` | domínio que importa algo além de `java.*`, do próprio domínio e de `common.money`/`common.error` |
| `layersPointInward` | dependência para fora: domínio → aplicação, ou aplicação → infraestrutura |
| `adaptersTalkToPorts` | adapter que usa a implementação do caso de uso em vez da porta (`…UseCase`, `…Port`) |
| `inboundAdaptersDoNotReachOutbound` | controller, consumidor ou job que chama direto um adapter de saída |
| `inboundPortsAreUseCases`, `outboundPortsAreNamedPort` | porta de entrada sem o sufixo `UseCase`; de saída sem `Port` |
| `servicesDoNotReachEachOther` | código de um serviço que importa classe de outro (só o `common` é compartilhado) |
| `jdbcOnlyInThePersistenceAdapter` | `JdbcClient` ou `SqlTime` fora de `adapters/out/persistence` e `config` |
| `cipherOnlyInThePersistenceAdapter` | `FieldCipher` ou `BlindIndexer` fora de `adapters/out/persistence` e `config` |
| `clockIsInjected` | `Instant.now()`, `LocalDateTime.now()` e afins, `Clock.systemUTC()` — a hora vem do `Clock` injetado |
| `accessRulesLiveInSecurityRoutes` | `@PreAuthorize`, `@Secured`, `@RolesAllowed`, `@EnableMethodSecurity` ou outro `SecurityFilterChain` |
| `noStandardStreams`, `noJavaUtilLogging` | `System.out`, `printStackTrace` e `java.util.logging`, que escapariam do log mascarado |
| `noFieldInjection` | `@Autowired` em campo — a dependência entra pelo construtor |

A regra de uma camada que o serviço ainda não tem passa vazia e começa a valer quando a camada nasce. O
`ServiceArchitectureRulesTests` do `common` prova cada regra contra um serviço de exemplo bem feito e
contra uma classe que a quebra. O próprio `common` tem as dele: `common.money` e `common.error` sem
Spring, e nenhuma classe que conheça um serviço.

### 3. Rodar um serviço

O `install` vem uma vez antes do primeiro `spring-boot:run` (põe o `common` no repositório local do
Maven). As variáveis dos serviços têm o valor local como padrão, **menos as senhas**: o serviço com banco
recebe as das *roles* do `infra/.env` — `DB_APP_PASSWORD` (aplicação, *role* `<serviço>_app`) e
`DB_OWNER_PASSWORD` (migrations, *role* `<serviço>_owner`). Com o ambiente local de pé:

O `vehicles-service` também lê a fila de comandos no LocalStack, e o cliente da AWS pega região, endereço
e credenciais das variáveis padrão do SDK. No local elas apontam para o LocalStack, que aceita as
credenciais `test`/`test` — não são segredo. Sem elas o serviço não sobe; para subir sem a fila, use
`VEHICLE_COMMANDS_ENABLED=false`.

```bash
./mvnw -B install -DskipTests
set -a; . infra/.env; set +a
export AWS_REGION=sa-east-1 AWS_ENDPOINT_URL=http://localhost:4566 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
DB_APP_PASSWORD="$VEHICLES_DB_APP_PASSWORD" DB_OWNER_PASSWORD="$VEHICLES_DB_OWNER_PASSWORD" \
  ./mvnw -pl vehicles-service spring-boot:run
```

No PowerShell:

```powershell
.\mvnw.cmd -B install -DskipTests
Get-Content infra/.env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }
$env:AWS_REGION = "sa-east-1"; $env:AWS_ENDPOINT_URL = "http://localhost:4566"; $env:AWS_ACCESS_KEY_ID = "test"; $env:AWS_SECRET_ACCESS_KEY = "test"
$env:DB_APP_PASSWORD = $env:VEHICLES_DB_APP_PASSWORD; $env:DB_OWNER_PASSWORD = $env:VEHICLES_DB_OWNER_PASSWORD
.\mvnw.cmd -pl vehicles-service spring-boot:run
```

O `customers-service` também tem banco e também fala com a AWS: usa o KMS do LocalStack (a chave
`alias/customers-data`, padrão de `KMS_CUSTOMER_KEY_ID`) e precisa das mesmas variáveis do SDK do
`vehicles` — sem elas não sobe. No mesmo terminal, troque só as senhas das *roles* (no PowerShell,
`$env:DB_APP_PASSWORD = $env:CUSTOMERS_DB_APP_PASSWORD; $env:DB_OWNER_PASSWORD = $env:CUSTOMERS_DB_OWNER_PASSWORD`
antes do `.\mvnw.cmd -pl customers-service spring-boot:run`):

```bash
DB_APP_PASSWORD="$CUSTOMERS_DB_APP_PASSWORD" DB_OWNER_PASSWORD="$CUSTOMERS_DB_OWNER_PASSWORD" \
  ./mvnw -pl customers-service spring-boot:run
```

Na subida, o Flyway cria ou atualiza as tabelas com a *role* `_owner` e a aplicação passa a usar a
`_app`, que só lê e grava dados. No `customers`, o dado pessoal fica em colunas cifradas pela aplicação
(`*_enc`), o CPF tem um *blind index* (HMAC) para a unicidade, e toda coluna pessoal leva o comentário
`[pessoal]` no banco.

As chaves dessa cifra são criadas na primeira subida do `customers`, antes de ele atender: para cada
finalidade (`FIELD`, a da cifra de campo, e `BLIND_INDEX`, a do índice) sem chave nenhuma, o KMS gera uma
chave de dados (`GenerateDataKey`) e só a forma embrulhada por ele vai para a `data_key`, na versão 1 e
ativa; a chave em claro não vai para o banco nem para o log, e cada contêiner a desembrulha uma vez, só em
memória. O log mostra `Chave de dados FIELD criada na versão 1` (e a do `BLIND_INDEX`); a segunda subida
não cria nada. Dois contêineres subindo juntos geram uma chave cada, mas o `INSERT … ON CONFLICT DO
NOTHING` deixa entrar só uma por finalidade — a chave primária e o índice de uma ativa por finalidade
barram a outra —, e o que perdeu segue com a que ficou. A chamada ao KMS acontece sem transação aberta.
Se uma finalidade tiver versões mas nenhuma ativa, a subida para com erro: escolher a versão ativa é
rotação, não primeira subida. Para conferir:

```bash
docker compose -f infra/docker-compose.yml exec postgres psql -U postgres -d customers \
  -c "SELECT purpose, version, active, length(encrypted_key) FROM data_key ORDER BY purpose"
```

```bash
curl http://localhost:8081/actuator/health
```

Resposta esperada: `{"groups":["liveness","readiness"],"status":"UP"}`. Só o `health` fica exposto;
os demais endpoints do Actuator respondem `404`.

### 4. Contrato (OpenAPI) e Swagger UI

Cada serviço publica o contrato gerado do código em `/v3/api-docs` e o Swagger UI em
`/swagger-ui.html` — no estoque, `http://localhost:8081/swagger-ui.html`. Os dois abrem sem token.
Para chamar uma rota da equipe, clique em **Authorize** e use o login `oidc` (o cliente `revenda-web`
já vem preenchido; deixe o *client secret* vazio): o Keycloak pede e-mail e senha de um usuário
sintético (seção "Usuários sintéticos") e devolve o token ao Swagger. Também dá para colar um token em
`bearer`. Rota interna pede token de serviço, que só se cola.

O contrato é montado em duas partes. O que vale para toda rota sai das regras de acesso do serviço (o
`SecurityRoutes`), na mesma ordem em que a segurança as aplica: quem pode chamar (escrito em **Acesso**
na descrição da rota), o login, o cabeçalho `X-Request-Id` e as respostas `401`, `403`, `429` e `500`.
Toda resposta de erro tem o corpo `ApiError` (`code`, `message`, `requestId`, `fields`). Assim o contrato
não sai do compasso da segurança. O que é da rota fica no controller: o resumo, a descrição com o caso de
uso e as regras, cada status com os `code` que ele pode trazer, a `Idempotency-Key` obrigatória no
cadastro e um exemplo sintético em cada campo. Rota aberta aceita o login, mas não exige: token enviado é
conferido, e a equipe logada vê o detalhe completo do veículo.

`OPENAPI_ENABLED=false` desliga o contrato e o Swagger UI (na nuvem eles não são publicados).

## Rotas prontas

| Serviço | Rota | Quem | O quê |
|---|---|---|---|
| `vehicles` | `POST /vehicles` | `SELLER`, `ADMIN` | cadastra um veículo à venda (`Idempotency-Key` obrigatória) |
| `vehicles` | `PUT /vehicles/{id}` | `SELLER`, `ADMIN` | edita um veículo à venda ou reservado, com a `version` lida |
| `vehicles` | `GET /vehicles` | qualquer um | vitrine: só à venda, do mais barato ao mais caro, com filtros e paginação |
| `vehicles` | `GET /vehicles/{id}` | qualquer um | detalhe: o público vê só o que está à venda; a equipe vê qualquer um |
| `vehicles` | `GET /vehicles/sold` | `SELLER`, `ADMIN` | vendidos e entregues, do menor para o maior preço de venda |
| `vehicles` | `POST /internal/vehicles/{id}/reservations` | serviço com *scope* `vehicles.reserve` | reserva o veículo para uma compra e devolve o preço do momento |

O cadastro confere marca, modelo, cor, quilometragem, anos (fabricação de 1900 ao ano que vem; modelo
igual ou no ano seguinte), preço (acima de zero e até R$ 10.000.000,00, em centavos), placa (padrão antigo
ou Mercosul, sem hífen), chassi (17 caracteres, sem `I`, `O` e `Q`) e RENAVAM (11 dígitos com o
verificador). Todos os erros voltam numa resposta só, em `fields`. Placa, chassi e RENAVAM não se repetem
no estoque (`409`). O cadastro grava uma linha de auditoria `VEHICLE_CREATED` com quem cadastrou.

A edição manda o veículo inteiro, com as mesmas regras do cadastro, mais a `version` que a tela leu. O
`UPDATE` só grava se o estoque ainda está nessa versão e o veículo segue à venda ou reservado
(`WHERE id = … AND version = … AND status IN ('AVAILABLE', 'RESERVED')`): quem editou antes vence, e o
outro recebe `409 VERSION_CONFLICT` — nunca há "ler e depois gravar" sem conferência. Veículo vendido ou
entregue não se edita (`409 VEHICLE_NOT_EDITABLE`). A edição não mexe no status nem na compra: mudar o
preço de um veículo reservado não muda o valor de quem o reservou, que foi fixado na reserva. A resposta
traz a `version` nova, e a auditoria `VEHICLE_UPDATED` guarda o veículo antes e depois.

As listagens são paginadas (`?page=0&size=20`, até 100 por página) e respondem
`{ items, page, size, totalItems }`. A ordem tem sempre o `id` como desempate (`ORDER BY price_cents, id`
na vitrine, `ORDER BY sold_price_cents, id` nos vendidos), então dois veículos com o mesmo preço nunca
trocam de lugar entre uma página e a outra. Cada ordem tem um índice parcial no banco, só com as linhas
daquele status. A vitrine filtra por marca e modelo (o nome inteiro, sem diferença de maiúsculas), faixa de
preço em centavos e faixa do ano do modelo, com as pontas incluídas.

O público nunca vê placa, chassi nem RENAVAM: a vitrine e o detalhe respondem com um item sem esses
campos, e o detalhe de um veículo reservado, vendido ou entregue responde `404` para quem não é da equipe.
A equipe vê o veículo inteiro em qualquer status, inclusive na lista de vendidos.

Rota aberta não precisa de token, mas um token que vem é conferido: vencido, adulterado ou de um cliente
não aceito responde `401`, como em qualquer rota. Ele nunca vira visitante em silêncio — assim, quem é da
equipe e teve a sessão vencida não vê a vitrine pública achando que é a dela. Ao receber `401` numa rota
aberta, o front descarta o token e repete o pedido sem `Authorization`.

### A reserva

A reserva é o passo da compra em que dois clientes podem disputar o mesmo carro. Ela é um único `UPDATE`
condicional — a cláusula `WHERE` é a regra:

```sql
UPDATE vehicle
   SET status = 'RESERVED', purchase_id = :purchaseId, version = version + 1, updated_at = :now
 WHERE id = :id AND status = 'AVAILABLE'
RETURNING price_cents;
```

O Postgres trava a linha durante o `UPDATE`: de vários pedidos ao mesmo tempo, o primeiro muda o status e
os outros, quando chegam à linha, já não a encontram `AVAILABLE` e não mudam nada. Não há "ler o status e
depois gravar", em que dois leriam `AVAILABLE` e os dois reservariam. Quem vence recebe `200` com o
`priceCents` daquele instante — é o preço que a compra vai cobrar, mesmo que a equipe edite o veículo
depois. Quem perde recebe `409 VEHICLE_UNAVAILABLE`. Um teste dispara dez reservas simultâneas do mesmo
veículo, vinte vezes seguidas, e confere que sempre exatamente uma vence.

Quando o `UPDATE` não muda nada, o serviço só lê a linha para responder: se ela já está reservada para a
**mesma** compra, a resposta é `200` sem efeito, porque a reserva é idempotente pelo `purchaseId` (por isso
a rota não pede `Idempotency-Key`). Qualquer outro caso — reservado por outra compra, vendido, entregue ou
um id que não existe — é `409`. A rota é interna: só o `sales-service`, com token de serviço e o *scope*
`vehicles.reserve`, chama; token de pessoa, mesmo da equipe, recebe `403`. Cada reserva grava a auditoria
`VEHICLE_RESERVED` com o serviço que pediu, a compra e o preço.

### Os comandos da fila

Depois da reserva, a compra muda o estoque por mensagens na fila FIFO `vehicle-commands.fifo`: liberar
(`ReleaseVehicle`), vender (`MarkVehicleSold`) e dar baixa (`DeliverVehicle`). O `sales-service` grava o
comando na mesma transação da mudança da compra e um despachante o envia depois; o `vehicles-service` lê
a fila. Cada mensagem é um envelope JSON, com o veículo como grupo da fila (`MessageGroupId`) — os
comandos de um mesmo carro chegam na ordem em que foram gravados:

```json
{ "id": "6f1c2a9e-…", "type": "MarkVehicleSold",
  "payload": { "vehicleId": "…", "purchaseId": "…", "soldPriceCents": 8990000, "soldAt": "2026-10-27T13:05:00Z" } }
```

| `type` | `payload` | Efeito |
|---|---|---|
| `ReleaseVehicle` | `vehicleId`, `purchaseId` | reservado ou vendido para esta compra → à venda de novo |
| `MarkVehicleSold` | `vehicleId`, `purchaseId`, `soldPriceCents`, `soldAt` | reservado para esta compra → vendido, pelo preço da reserva |
| `DeliverVehicle` | `vehicleId`, `purchaseId`, `deliveredAt` | vendido para esta compra → entregue (baixa no estoque) |

A fila entrega **pelo menos uma vez**: a mesma mensagem pode chegar de novo. Por isso o consumidor é
idempotente. O `id` da mensagem é gravado em `processed_message` na mesma transação do efeito e da
auditoria; se ele já está lá, a mensagem sai da fila sem mudar nada. Cada efeito é um `UPDATE` condicional
que só muda o veículo da **mesma** compra no estado certo (`WHERE id = … AND purchase_id = … AND status = …`),
como na reserva. Quando nada muda, o serviço lê o veículo só para decidir:

- **já em vigor** — vender o que já está vendido para esta compra, entregar o que já foi entregue, liberar o
  que já está à venda ou é de outra compra: a mensagem é confirmada e apagada, sem efeito nem auditoria.
  Liberar o veículo de outra compra nunca o libera: é o caso da liberação "por segurança" depois de uma
  reserva que não respondeu;
- **ainda não aplicável** — entregar um veículo que ainda está reservado para esta compra: a transação é
  desfeita (inclusive o `processed_message`), a mensagem fica na fila e volta depois do *visibility timeout*;
- **contraditório** — vender ou entregar para outra compra, ou um veículo que não existe, ou liberar um
  veículo já entregue: nada é aplicado, o log registra o erro e, depois de 5 recebimentos, a fila move a
  mensagem para a DLQ `vehicle-commands-dlq.fifo`. Mensagem ilegível tem o mesmo destino.

O consumidor lê em *long polling*, até 10 mensagens por vez, e só apaga a mensagem tratada. Se uma
mensagem fica na fila, as seguintes do mesmo veículo naquela leva também ficam, para nenhuma ser aplicada
fora de ordem. Cada efeito grava a auditoria `VEHICLE_RELEASED`, `VEHICLE_SOLD` ou `VEHICLE_DELIVERED` com
o ator `SYSTEM`, a compra e o `id` da mensagem. A mensagem e o log levam só ids, centavos e instantes.

Para testar à mão com o ambiente local e o `vehicles-service` de pé, mande um comando com o AWS CLI de dentro
do contêiner do LocalStack (os ids do veículo e da compra são os de uma reserva feita antes):

```bash
docker compose -f infra/docker-compose.yml exec localstack awslocal sqs send-message \
  --queue-url http://localhost:4566/000000000000/vehicle-commands.fifo \
  --message-group-id <vehicleId> --message-deduplication-id "$(date +%s)" \
  --message-body '{"id":"<uuid novo>","type":"MarkVehicleSold","payload":{"vehicleId":"<vehicleId>","purchaseId":"<purchaseId>","soldPriceCents":8990000,"soldAt":"2026-10-27T13:05:00Z"}}'
```

## Usuários sintéticos

Os usuários de teste e as senhas de desenvolvimento estão em
[`infra/keycloak/realm.json`](infra/keycloak/realm.json) — todos com e-mail `@example.test`:

| Usuário | Papel | Para testar |
|---|---|---|
| `ana.cliente@example.test` | `CUSTOMER` | compra com cadastro completo |
| `duda.cliente@example.test` | `CUSTOMER` | segunda compradora: reserva antes da Ana no cenário de reserva concorrente |
| `bruno.sem-cadastro@example.test` | `CUSTOMER` | compra sem cadastro (deve ser recusada) |
| `caio.menor@example.test` | `CUSTOMER` | comprador menor de idade (deve ser recusado) |
| `vera.vendedora@example.test` | `SELLER` | cadastro de veículos e confirmação da retirada |
| `otto.admin@example.test` | `ADMIN` | revelação de CPF com motivo e pedidos de titular |

No ambiente local, ninguém configura segundo fator: todos entram só com e-mail e senha, para quem avalia
o projeto conseguir usar o sistema sem um app autenticador. Na nuvem, o Cognito exige MFA de `SELLER` e
`ADMIN` — a equipe vê dados de clientes e revela CPF, e uma senha vazada sozinha não pode bastar para
isso. O Keycloak suporta a mesma regra: basta pôr `"requiredActions": ["CONFIGURE_TOTP"]` nesses dois
usuários do `realm.json` e recriar o contêiner. Quem se cadastra pela tela do Keycloak recebe o papel `CUSTOMER`. Os segredos dos clientes de serviço
vêm do `.env`.

O realm só é importado quando o contêiner do Keycloak é criado. Se o seu foi criado antes de um usuário
novo ou uma mudança entrar no `realm.json`, recrie-o — isso apaga as sessões e qualquer segundo fator já
configurado:

```bash
cd infra
docker compose up -d --force-recreate keycloak
```

## Testar com o Postman

A pasta [`postman/`](postman) tem a coleção com **todas as rotas da API**, cada uma com a descrição do
caso de uso, o papel e o que deve aparecer, e testes do contrato:

- `revenda-veiculos.postman_collection.json` — a coleção (formato Collection v2.1);
- `revenda-local.postman_environment.json` — o ambiente `revenda-local`: endereços locais e as variáveis
  secretas, todas vazias.

### Antes de começar

1. Use o **Postman desktop**: o Postman na web não alcança `localhost`.
2. Suba o ambiente local e os quatro serviços (seções acima), cada um num terminal — o serviço com banco
   com as senhas do `infra/.env`, como na seção "Rodar um serviço":

   ```bash
   ./mvnw -B install -DskipTests
   ./mvnw -pl vehicles-service spring-boot:run
   ./mvnw -pl customers-service spring-boot:run
   ./mvnw -pl sales-service spring-boot:run
   ./mvnw -pl payment-gateway-mock spring-boot:run
   ```

3. **Import** → os dois arquivos de `postman/`. No canto superior direito, escolha o ambiente
   `revenda-local`.
4. No ambiente, preencha o **valor atual** (*Current value*) das variáveis secretas com os valores do
   seu `infra/.env`. O valor atual fica só na sua máquina; não exporte nem versione o ambiente preenchido.

   | Variável do ambiente | Valor do `infra/.env` | Para quê |
   |---|---|---|
   | `salesServiceClientSecret` | `SALES_SERVICE_CLIENT_SECRET` | token de serviço do `sales-service` |
   | `customersServiceClientSecret` | `CUSTOMERS_SERVICE_CLIENT_SECRET` | token de serviço do `customers-service` |
   | `paymentGatewayApiKey` | `PAYMENT_GATEWAY_API_KEY` | `X-Api-Key` do gateway simulado |
   | `paymentWebhookSecret` | `PAYMENT_WEBHOOK_SECRET` | assinar um webhook velho (teste de *replay*) |

   As variáveis `token…` também são secretas: a coleção as preenche sozinha.

### Entrar com cada pessoa

A pasta **00 · Entrar** tem uma requisição por pessoa. O login é OAuth 2.0 *Authorization Code* com
PKCE no cliente público `revenda-web` do Keycloak — sem segredo, e nenhuma senha fica na coleção: os
usuários e as senhas de desenvolvimento estão só em
[`infra/keycloak/realm.json`](infra/keycloak/realm.json) (tabela de usuários acima).

1. Abra a requisição da pessoa → aba **Authorization** → **Clear cookies** (o Keycloak guarda a sessão de
   quem entrou por último).
2. **Get New Access Token**. A janela de login é a do próprio Postman — deixe **Authorize using
   browser** desligado; o retorno vai para `http://localhost:8083/oauth2/callback/postman`, que o realm
   aceita.
3. Entre com o e-mail da pessoa e a senha do `realm.json`. No local não há segundo fator.
4. **Use Token** → **Send**. O teste confere o papel e guarda o token no ambiente (`tokenAna`,
   `tokenDuda`, `tokenBruno`, `tokenCaio`, `tokenVera`, `tokenOtto`).

O token de acesso vale 15 minutos. Venceu (as rotas respondem `401`)? Peça outro na mesma requisição e
mande-a de novo.

### Ordem das pastas

Depois da 00, rode cada pasta no **Runner** (*Run folder*), nesta ordem — os ids passam de uma
requisição para a outra pelas variáveis da coleção (`vehicleId`, `customerId`, `purchaseId`,
`chargeId`…):

| Pasta | O que mostra |
|---|---|
| 01 · Ambiente e segurança | `health` dos quatro serviços; `401` sem token ou com token inválido; `403` de token de pessoa em rota interna; `X-Request-Id` |
| 02 · Estoque (vendedora) | cadastrar e editar veículo, `Idempotency-Key` (repetida e sem ela), validação, `version`, vendidos |
| 03 · Vitrine pública | vitrine por preço crescente, filtros, paginação, sem placa nem chassi para o visitante |
| 04 · Cadastro do cliente | cadastro com CPF mascarado, e-mail do token, CPF repetido ou inválido |
| 05 · Compra — fluxo feliz | reservar → simular o Pix no gateway → acompanhar → retirada com conferência de CPF → baixa no estoque → documento |
| 06 · Compra — falhas | reserva concorrente, uma compra ativa por cliente, desistência antes e depois de pagar, valor errado, webhook duplicado, sem cadastro, menor de idade, webhook sem assinatura, expiração e pagamento atrasado |
| 07 · Equipe | cliente mascarado para a vendedora, CPF revelado pelo admin com motivo, todas as compras |
| 08 · LGPD | exportar os próprios dados, pedido de exclusão |
| 09 · Gateway simulado | cobrança com `X-Api-Key`: criar, idempotência por `referenceId`, buscar, cancelar |
| 10 · Rotas internas | as rotas `/internal/**` com token de serviço |
| 99 · Limite de taxa | estoura o limite de `POST /purchases` e `POST /customers` — **por último** |

- O Runner espera sozinho: espaça os `POST /purchases` (10 por minuto) e `POST /customers` (5 por
  minuto) para não esbarrar no limite de taxa, e repete por até 20 s as consultas que esperam um efeito
  assíncrono (webhook, fila, documento). A pasta 04 leva cerca de um minuto por isso.
- A expiração (06.8) só acontece em tempo de teste com o prazo de pagamento curto: suba o `sales` com
  `PAYMENT_WINDOW=PT1M`. Com o prazo padrão (30 minutos), a pasta desiste da compra e segue.
- Os dados são sintéticos: CPFs com dígito válido fixos nas variáveis da coleção, e-mails
  `@example.test`, e placa, chassi e RENAVAM novos a cada execução. A coleção pode rodar quantas vezes
  quiser; nas seguintes, o cadastro já existe e responde `409 CUSTOMER_ALREADY_REGISTERED`, o que os
  testes aceitam.
- Toda resposta dos serviços passa pelos testes `[contrato]`: `X-Request-Id` no cabeçalho, erro no
  formato `{ code, message, requestId, fields }` com o mesmo `requestId`, dinheiro em centavos inteiros.

**Rota ainda não implementada** responde `401` sem token e `404` com token, e os testes dela falham até a
etapa que a cria. Hoje passam inteiras a 01 (com os quatro serviços de pé), a 02 e a 03 (com o
`vehicles-service` de pé e, na 02, o token de serviço que a 01 guarda).

### Pela linha de comando

A pasta 01 roda também no [Newman](https://github.com/postmanlabs/newman), sem login de pessoa (o teste
que precisa do token da Ana é pulado), com os segredos lidos do `infra/.env`:

```bash
set -a; . infra/.env; set +a
npx newman run postman/revenda-veiculos.postman_collection.json \
  -e postman/revenda-local.postman_environment.json \
  --env-var "salesServiceClientSecret=$SALES_SERVICE_CLIENT_SECRET" \
  --env-var "customersServiceClientSecret=$CUSTOMERS_SERVICE_CLIENT_SECRET" \
  --folder "01 · Ambiente e segurança"
```

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
├── postman/                 coleção com todas as rotas e o ambiente local
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
  wrapper, em `.github/` ou em `infra/postgres/` (que prepara o banco dos testes) roda todos; mudança só
  no resto de `infra/` ou na documentação não roda nenhum. A
  escolha fica em [`.github/scripts/affected-modules.sh`](.github/scripts/affected-modules.sh).
- Em *push* na `main`, rodam todos.
- O job `resultado` resume os demais e é o *check* a exigir na proteção da `main` — serviço que não
  precisou rodar conta como aprovado.
- Os relatórios de teste de cada serviço ficam como artefato por 14 dias.
