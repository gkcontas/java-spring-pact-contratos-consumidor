# Testes de contrato dirigidos pelo consumidor com Pact

Um provedor e dois consumidores. O `catalog-service` expõe produtos e reservas de
estoque; o `order-service` cria pedidos a partir dele e o `shipping-service` cota
frete pelo peso. Cada consumidor declara, num teste, **o que lê** do catálogo. Essa
declaração vira um contrato (um arquivo Pact), o provedor é verificado contra todos
os contratos com a aplicação real e banco real, e o **Pact Broker** guarda as
versões, os resultados de verificação e o que está implantado em cada ambiente
para responder à pergunta que importa antes do deploy: *posso subir esta versão?*

Complementa o projeto 17, que compara o documento OpenAPI gerado com um snapshot.
Lá o provedor compara a API consigo mesma; aqui quem define o que é quebra é quem
consome. A diferença foi medida: das 11 mudanças de API testadas, o snapshot de
esquema acusou 10 e o Pact acusou 5 — exatamente as 5 que quebram algum consumidor.

## Tecnologias

- **Java 21** e **Spring Boot 3.5** (Web, Validation, Data JPA, Actuator)
- **Pact JVM 4.7** — `consumer:junit5` nos consumidores, `provider:junit5spring`
  no provedor, especificação Pact V4
- **Pact Broker 2.124** (imagem `pactfoundation/pact-broker`)
- **PostgreSQL 16** e **Flyway** no provedor
- **Testcontainers** (Postgres e Pact Broker) e **JUnit Platform Launcher**
- **Gradle** (Kotlin DSL) multi-módulo com wrapper

## Os três serviços

```
                 GET /api/products/{sku}       lê: sku, name, price
order-service ──► POST /api/stock-reservations  lê: reservationId; 409 + code + available
   :8081                                      │
                                              ▼
                                       catalog-service :8080 ──► PostgreSQL
                                              ▲
shipping-service ──► GET /api/products/{sku}  lê: sku, weightGrams
   :8082
```

| Serviço | Rota | O que faz |
| --- | --- | --- |
| catalog | `GET /api/products/{sku}` | produto com `name`, `description`, `price`, `weightGrams`, `stock` |
| catalog | `POST /api/stock-reservations` | reserva estoque; 201, 404, 409 `INSUFFICIENT_STOCK` com `available`, 400 |
| order | `POST /api/orders` | busca o produto, reserva, devolve o pedido com o total |
| shipping | `GET /api/shipping-quotes?sku=&quantity=` | R$ 12,00 + R$ 4,50 por quilo iniciado |

Os três são aplicações independentes, cada uma com o seu jar. Ficam no mesmo
repositório só para o contrato circular entre eles sem broker no build; nenhum
módulo enxerga o código do outro. Em repositórios separados, o broker seria o
único canal — e a verificação do provedor seria a mesma.

O mesmo endpoint tem dois contratos diferentes. O pedido lê preço e nome; o frete
lê só o peso. O provedor precisa satisfazer os dois ao mesmo tempo, e pode mudar
livremente tudo o que nenhum dos dois lê.

## O contrato descreve o que o consumidor lê

```java
.given("a product exists", Map.of("sku", "KB-001", "stock", 10))
.uponReceiving("a request for an existing product")
    .path("/api/products/KB-001").method("GET")
.willRespondWith()
    .status(200)
    .body(new PactDslJsonBody()
            .stringType("sku", "KB-001")
            .stringType("name", "Mechanical keyboard")
            .decimalType("price", new BigDecimal("349.90")))
```

Três decisões nesse trecho:

- **Só os campos que o código lê.** `description`, `weightGrams` e `stock` não
  aparecem no contrato do pedido. O provedor pode removê-los ou renomeá-los sem
  quebrar o pedido — e o broker sabe disso.
- **Matchers por tipo, não valores.** `stringType` e `decimalType` dizem "uma
  string", "um decimal"; o exemplo é o que o mock server responde, não o que o
  provedor tem que devolver. Contrato com valor literal depende dos dados do teste
  e quebra sem que ninguém tenha mudado a API.
- **Valor literal só onde o código decide por ele.** No 409, o `code` é
  `stringValue("code", "INSUFFICIENT_STOCK")`: o cliente faz `if` nesse valor, então
  ele é contrato. O `detail` legível não entra.

O teste do consumidor exercita **o cliente HTTP de produção** (`CatalogClient`)
contra o mock server do Pact, construído com o `RestClient.Builder` do Spring Boot.
Isso importa duas vezes: um teste que montasse a requisição à mão publicaria o
contrato de um código que não roda em produção; e o builder do Boot traz o
`ObjectMapper` que ignora campos desconhecidos. Um `ObjectMapper` cru falharia no
primeiro campo novo do provedor, e o contrato, que diz "campos novos não quebram",
estaria mentindo sobre o cliente.

O contrato também não testa regra de negócio. Ele diz que o catálogo devolve um
decimal em `price`; o que o pedido faz com esse preço (o total) é teste de unidade
do `OrderService`.

## A verificação do provedor

`CatalogProviderPactTest` sobe a aplicação inteira (controllers, JPA, Postgres em
Testcontainers) e reproduz cada interação de cada contrato. Cada interação vira um
teste com o nome do consumidor e da interação.

Os *provider states* (`"a product exists"`, com `sku` e `stock` como parâmetros)
preparam o banco do provedor. O consumidor só nomeia o estado; quem sabe montá-lo
é o provedor. Eles ficam no **código de teste**, não atrás de um endpoint
`/_pact/state` na aplicação: um endpoint que reescreve estoque é um risco em
produção no dia em que alguém esquecer o perfil que o liga.

No build, o Gradle copia os contratos dos módulos consumidores para
`catalog-service/build/consumer-pacts` antes dos testes do provedor; contrato é
saída de build do consumidor, como um jar.

## Pact x snapshot de esquema, medido

`ContractChangeMatrixTest` aplica 11 mudanças que um time de provedor poderia
fazer — uma por vez, por um filtro de teste que reescreve as respostas reais — e,
para cada uma, roda duas coisas:

- a **verificação Pact** de verdade (a mesma classe do build, disparada pelo
  JUnit Launcher), anotando quais consumidores falharam;
- um **snapshot de esquema** das mesmas quatro respostas: status, campos e tipo
  JSON de cada campo — o que um snapshot de OpenAPI captura.

O teste afirma que o Pact acusa exatamente os consumidores que de fato leem o que
mudou, e grava a matriz em `catalog-service/build/reports/contract-change-matrix.txt`:

| Mudança no provedor | Pact: consumidor quebrado | Snapshot |
| --- | --- | --- |
| produto ganha `category` | — | mudou |
| produto perde `description` (ninguém lê) | — | mudou |
| `stock` vira `availableStock` | — | mudou |
| produto perde `weightGrams` | **shipping-service** | mudou |
| `price` vira `unitPrice` | **order-service** | mudou |
| `price` sai como string | **order-service** | mudou |
| 404 perde `title` | — | mudou |
| reserva perde `remainingStock` | — | mudou |
| 409 ganha `traceId` | — | mudou |
| falta de estoque responde 422 | **order-service** | mudou |
| `INSUFFICIENT_STOCK` vira `OUT_OF_STOCK` | **order-service** | **igual** |

| | Acusou | Falsos positivos | Quebras perdidas |
| --- | --- | --- | --- |
| Pact | 5 de 11 | 0 | 0 |
| Snapshot de esquema | 10 de 11 | 6 | 1 |

O snapshot erra nos dois sentidos. Acusa toda mudança aditiva ou em campo que
ninguém lê — e um alarme que dispara em 6 de 10 mudanças inofensivas ensina o time
a atualizar o snapshot sem ler. E deixa passar a única mudança que não altera a
forma: renomear o **valor** de um código de erro. O pedido decide por esse valor;
o esquema não sabe disso, o contrato sabe.

O Pact também diz **quem** quebra: remover `weightGrams` só afeta o frete. O
snapshot diz apenas que algo mudou.

O preço dessa precisão é que o Pact só protege o que algum consumidor declarou.
Uma API pública, com consumidores que não escrevem contrato, continua precisando
de snapshot ou de versionamento — os dois projetos se complementam.

## O broker: versões, ambientes e `can-i-deploy`

O build verifica contra os arquivos locais. Um pipeline de verdade usa o broker:

1. O consumidor publica o contrato com a versão (o commit) e a branch.
2. O provedor verifica o que os **consumer version selectors** pedem —
   `mainBranch()` (o que vai subir) e `deployedOrReleased()` (o que está no ar) —
   e publica o resultado com a própria versão.
3. Antes de cada deploy, `can-i-deploy` pergunta ao broker se a versão é
   compatível com tudo o que está no ambiente de destino.
4. Depois do deploy, `record-deployment` diz ao broker o que está no ar.

`CatalogBrokerVerification` é a verificação a partir do broker; ela só roda com
`-Dpactbroker.url`. `PactBrokerWorkflowIT` conta a vida de um contrato entre dois
times contra um broker real em Testcontainers (as versões do provedor `c2` e `c3`
são simuladas pelo mesmo filtro da matriz):

| Passo | `can-i-deploy` para production |
| --- | --- |
| consumidores publicam `o1` e `s1` da main | `o1`: **desconhecido** — nenhum provedor no ar |
| provedor `c1` verifica: 6 de 6 | `c1`: sim — nenhum consumidor no ar ainda |
| `c1` implantado | `o1`: sim · `s1`: sim |
| pedido `o2`, numa branch, passa a ler `category` | `o2`: **desconhecido** |
| provedor `c1` verifica de novo: o contrato de `o2` falha, **o build passa** | `o2`: **não** — falhou contra `c1` |
| provedor `c2` adiciona `category`: 10 de 10 | `o2`: **não** — `c2` não está no ar |
| `c2` implantado | `c2`: sim · `o2`: sim |
| provedor `c3` remove `weightGrams`: 9 de 10, **o build falha** | `c3`: **não** — `s1`, no ar, lê o peso |

Os pontos que essa sequência mostra:

- **Pending pacts.** Um contrato que o provedor nunca verificou com sucesso na
  main pode falhar sem quebrar o build do provedor. O consumidor não consegue
  travar o pipeline de outro time publicando uma expectativa; ele é que fica
  bloqueado no `can-i-deploy`. O contrato do `s1`, ao contrário, já foi verificado
  e está no ar: quebrá-lo (`c3`) falha o build.
- **WIP pacts.** Com `includeWipPactsSince`, o provedor verifica também contratos
  de branches de consumidores que os seletores não pegariam. O time do pedido
  descobre na hora se o provedor já atende a branch, sem pedir nada a ninguém.
- **A ordem do deploy é imposta.** O consumidor que passa a exigir um campo só
  sobe depois que um provedor com esse campo **estiver no ar** — verificado não
  basta.
- **Desconhecido não é sim.** Sem verificação, o broker responde
  `deployable: null`, não `false`. Um script que só barrasse em `false` subiria
  uma versão nunca verificada; `scripts/broker.sh can-i-deploy` só sai com 0 em
  `true`.

## As medições

Setup: Corretto 21 a partir dos jars empacotados, Postgres e Pact Broker (sobre
Postgres) do `docker-compose.yml`, daemon do Gradle parado nas medições de
latência, máquina de 12 núcleos. Tudo numa única sessão.

**O fluxo no broker real.** O mesmo roteiro do `PactBrokerWorkflowIT` foi
executado contra o broker do compose: contratos publicados com
`scripts/broker.sh`, verificação do provedor pelo Gradle
(`-Dpactbroker.url=http://localhost:9292`), `can-i-deploy` e `record-deployment`
pelo script. `c1` verificou 6 de 6 e liberou `o1` e `s1`; `c3` sem `weightGrams`
falhou 1 interação, o Gradle saiu com erro e o `can-i-deploy` respondeu não
citando o `s1` em produção. Cada chamada ao broker levou de 38 a 135 ms; a
verificação completa via Gradle, 16 s de relógio (Gradle, contexto Spring e
Postgres em container), dos quais 1,0 s nas 6 interações.

**O custo do feedback.**

| O que roda | Precisa de | Tempo |
| --- | --- | --- |
| contrato do pedido (4 interações) | nada além do JVM | 1,0 s |
| contrato do frete (2 interações) | nada além do JVM | 0,57 s |
| verificação do provedor (6 interações, contexto aquecido) | Postgres | 63 ms |
| uma das 11 verificações da matriz | Postgres | 57 a 70 ms |

O teste do consumidor não sobe o catálogo, nem banco, nem broker. Um teste de
ponta a ponta do pedido precisaria dos três serviços e do Postgres de pé — aqui o
catálogo levou 7,5 s para subir e o pedido 5,2 s. Pelos jars, as latências
medidas (300 chamadas de aquecimento descartadas, 2 000 medidas, uma conexão):

| Chamada | Saltos | p50 | p99 |
| --- | --- | --- | --- |
| `GET /api/products/KB-001` no catálogo | 1 | 1,25 ms | 2,36 ms |
| `GET /api/shipping-quotes` | 2 | 2,71 ms | 5,22 ms |
| `POST /api/orders` | 3 (consulta + reserva) | 5,95 ms | 16,83 ms |

O contrato não substitui o teste de ponta a ponta de um fluxo crítico, mas tira
dele a obrigação de pegar incompatibilidade de API — que é a maior parte do que
quebra entre serviços.

## Testes

38 testes; 1 ignorado de propósito (`CatalogBrokerVerification`, que só roda com
URL de broker). Postgres e Pact Broker sobem em containers singleton.

| Classe | O que prova |
| --- | --- |
| `order-service` `CatalogClientPactTest` | 4 interações: produto existente, 404, reserva 201, 409 com `code` e `available` |
| `shipping-service` `CatalogClientPactTest` | 2 interações: peso do produto e 404 |
| `CatalogProviderPactTest` | as 6 interações dos dois contratos contra app e Postgres reais |
| `ContractChangeMatrixTest` | a matriz de 11 mudanças, Pact x snapshot |
| `PactBrokerWorkflowIT` | publicação, verificação, pending/WIP, `can-i-deploy` e `record-deployment` num broker real |
| `CatalogApiIT` | regras do provedor: reserva atômica, 409 sem efeito colateral, 30 reservas concorrentes em estoque 10 = 10 aceitas |
| `OrderServiceTest`, `OrderControllerTest` | total do pedido, produto desconhecido não reserva, 422/409/503/400 |
| `QuoteServiceTest`, `QuoteControllerTest` | cobrança por quilo iniciado, 422/400 |

## Como rodar

```bash
export DOCKER_HOST=unix:///var/run/docker.sock   # se o contexto padrão do Docker não for o socket local
docker compose up -d --wait                      # Postgres 5432, Pact Broker 9292 (UI em http://localhost:9292)
./gradlew clean build                            # gera os contratos, verifica o provedor, monta os jars
java -jar catalog-service/build/libs/catalog-service-1.0.0.jar &
java -jar order-service/build/libs/order-service-1.0.0.jar &
java -jar shipping-service/build/libs/shipping-service-1.0.0.jar &
```

O pipeline do broker, passo a passo:

```bash
scripts/broker.sh publish order-service o1 main order-service/build/pacts/order-service-catalog-service.json
scripts/broker.sh publish shipping-service s1 main shipping-service/build/pacts/shipping-service-catalog-service.json

./gradlew :catalog-service:test --tests '*CatalogBrokerVerification' \
  -Dpactbroker.url=http://localhost:9292 -Dpact.provider.version=c1 \
  -Dpact.provider.branch=main -Dpact.verifier.publishResults=true

scripts/broker.sh can-i-deploy catalog-service c1 && scripts/broker.sh record-deployment catalog-service c1
scripts/broker.sh can-i-deploy order-service o1 && scripts/broker.sh record-deployment order-service o1
```

Para parar: encerrar os três processos e `docker compose down -v`.

## Exemplos

```bash
# produto direto no provedor
curl -s localhost:8080/api/products/MN-003

# pedido: 201 com total e reservationId; acima do estoque, 409 com "available"
curl -s -XPOST localhost:8081/api/orders -H 'Content-Type: application/json' \
  -d '{"sku":"MN-003","quantity":2}'

# produto inexistente: 422 no pedido (o 404 do catálogo vira "produto desconhecido")
curl -s -XPOST localhost:8081/api/orders -H 'Content-Type: application/json' \
  -d '{"sku":"XX-1","quantity":1}'

# frete: 3 teclados de 950 g = 2 850 g = 3 quilos iniciados = R$ 25,50
curl -s 'localhost:8082/api/shipping-quotes?sku=KB-001&quantity=3'
```

## Estrutura

```
catalog-service/                       # provedor
├── src/main/java/com/gkcontas/catalog/
│   ├── product/      # Product, ProductRepository (baixa de estoque atômica)
│   ├── reservation/  # ReservationService, StockReservation
│   └── web/          # CatalogController, ErrorHandler (ProblemDetail com code)
└── src/test/java/com/gkcontas/catalog/
    ├── contract/     # verificação Pact, provider states, matriz de mudanças
    └── broker/       # PactBrokerWorkflowIT, cliente HTTP do broker
order-service/                         # consumidor: pedidos
└── src/{main,test}/java/com/gkcontas/order/catalog/   # CatalogClient + contrato
shipping-service/                      # consumidor: frete
└── src/{main,test}/java/com/gkcontas/shipping/catalog/
scripts/broker.sh                      # publish, can-i-deploy, record-deployment
```
