# ATLAS — Diagramas UML principais

## Índice

| Nº | Diagrama | Objetivo | Arquivos |
| --- | --- | --- | --- |
| 01 | Classes · domínio do restaurante | Objetos de salão, catálogo, pedidos e pagamento em memória. | [Fonte](fontes/01-classes-dominio.puml) · [SVG](svg/01-classes-dominio.svg) |
| 02 | Classes · Backend e persistência | Ponto de entrada, autenticação, autorização, despacho de rotas e classes JDBC. | [Fonte](fontes/02-classes-backend.puml) · [SVG](svg/02-classes-backend.svg) |
| 03 | Classes · Desktop JavaFX | Ciclo da aplicação, controladores, consultas HTTP, operações e impressão. | [Fonte](fontes/03-classes-desktop.puml) · [SVG](svg/03-classes-desktop.svg) |
| 04 | Classes · Android | Atividades, rascunho persistente, atendimento, transporte HTTP e listener MQTT. | [Fonte](fontes/04-classes-android.puml) · [SVG](svg/04-classes-android.svg) |
| 05 | Casos de uso · sistema ATLAS | Ações do garçom, caixa, gerente e operação de implantação. | [Fonte](fontes/05-casos-de-uso.puml) · [SVG](svg/05-casos-de-uso.svg) |
| 06 | Componentes · arquitetura | Clientes, Backend, Modelo, PostgreSQL, MQTT e filas do sistema operacional. | [Fonte](fontes/06-componentes.puml) · [SVG](svg/06-componentes.svg) |
| 07 | Sequência · autenticação | Login, papel aceito no cliente, sessão protegida e encerramento. | [Fonte](fontes/07-seq-autenticacao.puml) · [SVG](svg/07-seq-autenticacao.svg) |
| 08 | Sequência · comandas e pedidos | Abertura, consumo, rascunho, gravação do pedido e repetição idempotente. | [Fonte](fontes/08-seq-comandas-pedidos.puml) · [SVG](svg/08-seq-comandas-pedidos.svg) |
| 09 | Sequência · HTTP, MQTT e notificações | Transação, publicação após commit e nova consulta pelos clientes. | [Fonte](fontes/09-seq-http-mqtt.puml) · [SVG](svg/09-seq-http-mqtt.svg) |
| 10 | Sequência · impressão | Resolução da fila, reserva, envio, resultado e conferência manual. | [Fonte](fontes/10-seq-impressao.puml) · [SVG](svg/10-seq-impressao.svg) |
| 11 | Sequência · pagamento e fechamento | Solicitação de conta, registro integral, encerramento e disponibilidade da mesa. | [Fonte](fontes/11-seq-pagamento.puml) · [SVG](svg/11-seq-pagamento.svg) |
| 12 | Estados · ciclo operacional | Transições persistidas de comanda, pedido e tarefa de impressão. | [Fonte](fontes/12-estados-operacionais.puml) · [SVG](svg/12-estados-operacionais.svg) |
| 13 | Persistência · esquema PostgreSQL | Nove tabelas, chaves, nulabilidade e cardinalidades. | [Fonte](fontes/13-esquema-postgresql.puml) · [SVG](svg/13-esquema-postgresql.svg) |
| 14 | Sequência · produção, preparo e entrega | Fila por setor, atualização do pedido pela gerência e entrega pelo garçom autor. | [Fonte](fontes/14-seq-producao-entrega.puml) · [SVG](svg/14-seq-producao-entrega.svg) |

## Renderização

Com Graphviz disponível, execute o comando abaixo na pasta `Principais`. O exemplo considera `plantuml.jar` nessa pasta e grava os arquivos em `svg/`.

```powershell
java -jar plantuml.jar -charset UTF-8 -failfast2 -tsvg -o ../svg fontes
```
