# ATLAS — Documentação UML

- [14 diagramas principais](Principais/README.md)
- [Auditoria de fidelidade](Auditoria.md)
- [Validação e integridade](Validacao.md)
- [Exportação copiável em Markdown dos 33 originais](ATLAS-UML-PlantUML.md)

## Índice do acervo

A coleção de referência tem 33 pares de fontes PlantUML e vetores SVG.

| Diagrama | Escopo | PlantUML | SVG |
| --- | --- | --- | --- |
| 01 · Domínio Java em memória | Visão geral dos objetos em memória. | [Fonte](Referencia-Completa/fontes/01-dominio.puml) | [Vetor](Referencia-Completa/svg/01-dominio.svg) |
| 02 · Classes do Modelo · atendimento e conta | Restaurante, Mesa, Comanda, Pedido e pagamento. | [Fonte](Referencia-Completa/fontes/02-modelo-atendimento.puml) | [Vetor](Referencia-Completa/svg/02-modelo-atendimento.svg) |
| 03 · Classes do Modelo · catálogo, item e funcionário | Catálogo, ItemPedido, Funcionario e enums. | [Fonte](Referencia-Completa/fontes/03-modelo-cardapio.puml) | [Vetor](Referencia-Completa/svg/03-modelo-cardapio.svg) |
| 04 · Classes do Backend · inicialização, HTTP e MQTT | Inicialização, HTTP, conexão JDBC e publicador MQTT. | [Fonte](Referencia-Completa/fontes/04-backend-entrada.puml) | [Vetor](Referencia-Completa/svg/04-backend-entrada.svg) |
| 05 · Classes do Backend · sessão, credenciais e autorização | Funcionários, credenciais, sessões e política de acesso. | [Fonte](Referencia-Completa/fontes/05-backend-acesso.puml) | [Vetor](Referencia-Completa/svg/05-backend-acesso.svg) |
| 06 · Classes do Backend · rotas, entrada e acesso JDBC | Roteamento, validação e utilitários JDBC. | [Fonte](Referencia-Completa/fontes/06-backend-rotas.puml) | [Vetor](Referencia-Completa/svg/06-backend-rotas.svg) |
| 07 · Classes do Backend · salão, catálogo e comanda | Catálogo, salão, consultas e transações de comanda. | [Fonte](Referencia-Completa/fontes/07-backend-salao.puml) | [Vetor](Referencia-Completa/svg/07-backend-salao.svg) |
| 08 · Classes do Backend · pedidos e tarefas por setor | Registro e consulta de pedidos, persistência das tarefas de impressão. | [Fonte](Referencia-Completa/fontes/08-backend-producao.puml) | [Vetor](Referencia-Completa/svg/08-backend-producao.svg) |
| 09 · Classes do Desktop · aplicação, sessão e transporte | Aplicação JavaFX, login e transporte HTTP. | [Fonte](Referencia-Completa/fontes/09-desktop-sessao.puml) | [Vetor](Referencia-Completa/svg/09-desktop-sessao.svg) |
| 10 · Classes do Desktop · painel, navegação e listagem | Controladores, navegação, listagem e avisos MQTT. | [Fonte](Referencia-Completa/fontes/10-desktop-painel.puml) | [Vetor](Referencia-Completa/svg/10-desktop-painel.svg) |
| 11 · Classes do Desktop · cadastros, financeiro e impressão | Formulários, cadastros, financeiro e impressão local. | [Fonte](Referencia-Completa/fontes/11-desktop-operacoes.puml) | [Vetor](Referencia-Completa/svg/11-desktop-operacoes.svg) |
| 12 · Classes do Android · login, atendimento e callbacks | Atividades, estado de tela e interfaces de callback. | [Fonte](Referencia-Completa/fontes/12-mobile-atividade.puml) | [Vetor](Referencia-Completa/svg/12-mobile-atividade.svg) |
| 13 · Classes do Android · salão, montagem e rascunho | Salão, cardápio, montagem, adaptador e rascunho. | [Fonte](Referencia-Completa/fontes/13-mobile-salao.puml) | [Vetor](Referencia-Completa/svg/13-mobile-salao.svg) |
| 14 · Classes do Android · HTTP, configuração e MQTT | Cliente HTTP, preferências e callbacks MQTT. | [Fonte](Referencia-Completa/fontes/14-mobile-rede.puml) | [Vetor](Referencia-Completa/svg/14-mobile-rede.svg) |
| 15 · Classes de verificação existentes | Verificações de domínio, HTTP e integração JDBC. | [Fonte](Referencia-Completa/fontes/15-testes.puml) | [Vetor](Referencia-Completa/svg/15-testes.svg) |
| 20 · Autenticação e autorização | Login, validação de sessão e autorização das rotas. | [Fonte](Referencia-Completa/fontes/20-seq-autenticacao.puml) | [Vetor](Referencia-Completa/svg/20-seq-autenticacao.svg) |
| 21 · Abertura da comanda e consulta de consumo | Mesa, abertura explícita e consulta de consumo. | [Fonte](Referencia-Completa/fontes/21-seq-abertura.puml) | [Vetor](Referencia-Completa/svg/21-seq-abertura.svg) |
| 22 · Montagem, envio e recuperação no Android | Montagem, armazenamento e reenvio pelo Android. | [Fonte](Referencia-Completa/fontes/22-seq-envio-android.puml) | [Vetor](Referencia-Completa/svg/22-seq-envio-android.svg) |
| 23 · Registro transacional e idempotência do pedido | Transação do pedido, idempotência e criação de tarefas. | [Fonte](Referencia-Completa/fontes/23-seq-registro-pedido.puml) | [Vetor](Referencia-Completa/svg/23-seq-registro-pedido.svg) |
| 24 · HTTP, commit e notificações MQTT | Commit, aviso MQTT e nova consulta HTTP. | [Fonte](Referencia-Completa/fontes/24-seq-http-mqtt.puml) | [Vetor](Referencia-Completa/svg/24-seq-http-mqtt.svg) |
| 25 · Encaminhamento de comprovantes para Bar e Cozinha | Tarefas e comprovantes para Bar e Cozinha. | [Fonte](Referencia-Completa/fontes/25-seq-encaminhamento.puml) | [Vetor](Referencia-Completa/svg/25-seq-encaminhamento.svg) |
| 26 · Atualização do preparo e registro da entrega | Preparo global do pedido e entrega pelo autor. | [Fonte](Referencia-Completa/fontes/26-seq-preparo-entrega.puml) | [Vetor](Referencia-Completa/svg/26-seq-preparo-entrega.svg) |
| 27 · Impressão manual e registro do envio | Reserva e envio local ao Java Print Service. | [Fonte](Referencia-Completa/fontes/27-seq-impressao-envio.puml) | [Vetor](Referencia-Completa/svg/27-seq-impressao-envio.svg) |
| 28 · Conferência e repetição de impressão | Resultado, conferência e nova tentativa de impressão. | [Fonte](Referencia-Completa/fontes/28-seq-impressao-conferencia.puml) | [Vetor](Referencia-Completa/svg/28-seq-impressao-conferencia.svg) |
| 29 · Solicitação da conta, pagamento e liberação da mesa | Solicitação da conta, recebimento e liberação da mesa. | [Fonte](Referencia-Completa/fontes/29-seq-pagamento.puml) | [Vetor](Referencia-Completa/svg/29-seq-pagamento.svg) |
| 30 · Casos de uso · atendimento Android | Operações do garçom no Android. | [Fonte](Referencia-Completa/fontes/30-uc-atendimento.puml) | [Vetor](Referencia-Completa/svg/30-uc-atendimento.svg) |
| 31 · Casos de uso · salão, caixa e histórico no Desktop | Salão, financeiro e histórico no Desktop. | [Fonte](Referencia-Completa/fontes/31-uc-salao-caixa.puml) | [Vetor](Referencia-Completa/svg/31-uc-salao-caixa.svg) |
| 32 · Casos de uso · gestão, produção e acesso inicial | Cadastros, produção, impressão e acesso inicial. | [Fonte](Referencia-Completa/fontes/32-uc-gerencia-producao.puml) | [Vetor](Referencia-Completa/svg/32-uc-gerencia-producao.svg) |
| 33 · Componentes e interfaces em operação | Componentes, protocolos e limites das integrações. | [Fonte](Referencia-Completa/fontes/33-componentes.puml) | [Vetor](Referencia-Completa/svg/33-componentes.svg) |
| 34 · Esquema PostgreSQL · tabelas e cardinalidades | Tabelas, chaves, nulabilidade e cardinalidades. | [Fonte](Referencia-Completa/fontes/34-postgresql.puml) | [Vetor](Referencia-Completa/svg/34-postgresql.svg) |
| 35 · Estados persistidos da comanda e situação da mesa | Transições de comanda e efeito sobre a mesa. | [Fonte](Referencia-Completa/fontes/35-estados-comanda.puml) | [Vetor](Referencia-Completa/svg/35-estados-comanda.svg) |
| 36 · Estados persistidos do pedido | Transições de preparo e entrega. | [Fonte](Referencia-Completa/fontes/36-estados-pedido.puml) | [Vetor](Referencia-Completa/svg/36-estados-pedido.svg) |
| 37 · Estados persistidos da tarefa de impressão | Transições e guardas da tarefa de impressão. | [Fonte](Referencia-Completa/fontes/37-estados-impressao.puml) | [Vetor](Referencia-Completa/svg/37-estados-impressao.svg) |
