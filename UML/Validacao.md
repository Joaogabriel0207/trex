# ATLAS — Validação da documentação UML

## Integridade do acervo

- Comparação SHA-256 por nome de arquivo entre `UML/fontes/` e `UML/Referencia-Completa/fontes/`. Os **33 pares** de fontes tinham o mesmo hash.
- Cópia e verificação dos **33 SVGs originais** em `Referencia-Completa/svg/`. O acervo final tem **33 PlantUML e 33 SVGs**. As duas pastas antigas foram enviadas à Lixeira após verificar os pares canônicos.
- Comparação antes e depois da operação nos **406 arquivos** do projeto bruto. Os hashes permaneceram iguais.

## Renderização e revisão visual

- **PlantUML 1.2025.10**, **Graphviz 2.44.1**, Java 21 e **PyMuPDF** para inspeção das prévias. Os quatro diagramas de classes usam o layout Smetana do PlantUML.
- Os 14 PlantUML principais e os 33 do acervo foram renderizados para conferência. Os vetores do acervo e os dez SVGs principais fora da revisão de classes conservaram seus arquivos originais.
- A revisão dos diagramas 01 a 04 conferiu posição das classes, separação entre níveis, cruzamentos, rótulos e multiplicidades. Foram comparadas as declarações e as relações antes e depois do ajuste, incluindo os tipos de seta, a navegabilidade e as cardinalidades.
- Os caminhos locais nos índices e na auditoria foram verificados. A atualização para caminhos do repositório aguarda a definição da estrutura definitiva.

## Resultados anteriores e limitações

Na elaboração da modelagem, Modelo, Backend, Desktop e testes compilaram com alvo Java 17. `TesteDominio` passou em 50 verificações, `TesteServico` em 29 e `TesteFluxoHttp` executou o esquema em PostgreSQL 19 temporário. No Android, o AAPT2 processou os recursos, e as dez fontes foram compiladas com SDK 35. [Verificação do projeto](../../Atlas%20v.2.0/Documentacao/Verificacao.md)

O acabamento atual verificou a documentação. Continuam pendentes a execução com Mosquitto, o teste em aparelho Android e a confirmação da impressão em dispositivo físico. O estado `ENVIADA` descreve a aceitação pela fila do sistema operacional.

<details>
<summary>SHA-256 dos 33 pares do acervo</summary>

| Diagrama | SHA-256 da fonte | SHA-256 do SVG |
| --- | --- | --- |
| `01-dominio` | `ba22a9de6a5f396882b11c4b4d48470f34ac214531db14c71045c3c2a34685b2` | `0ac764bc99ea2eba800b87506e3fc5d4c465603a3bb6ca0eae15286fe45e2f13` |
| `02-modelo-atendimento` | `9e472d160de8e24e1a25a121d7208c2dbe1b1d3de6ebfe8111cf6c12ba119556` | `952f82193b57bfa77baa7f16278be44216963e64bcc6c60e665639ed73b33039` |
| `03-modelo-cardapio` | `435c7064a7ce6bcf62ef04c08c8721a746f30d921972e5c53285223fada7fec7` | `04271edeec8784a3ed63493214cb816614a1d375e92ce4ad51dc53524d74adca` |
| `04-backend-entrada` | `ed5837f3b7d6e74b8b95b82b478f3af32eb16192aa059dace2057a84fc714f95` | `ebd41c19a3050ff6a7bb779083360ce314fb645f689a11e291bb0340f00076f4` |
| `05-backend-acesso` | `679730d9c9270dda011486e5d44855354c168a76abd058f21c32785575e80b7a` | `69d673630774697f6f98d6574083de85f57d94551a81edf247a57c0a366c9c3b` |
| `06-backend-rotas` | `1fcc3ff7e2ad6bb132415d0f65201867b6b1963190452c0d1a090ff4a5b31580` | `7c1bae11856fbedc9baec2883b830bb7d781f43472e7a315c08184d2e4daece6` |
| `07-backend-salao` | `b08fa96d600c77c16f17495dd8f0dd46ca59256636dec6fb713c24436e040936` | `f2c4e0bd923934ea4275ce97546d40cd77694c1fd700637c50aa76792117ab21` |
| `08-backend-producao` | `382e028bf5d802899f892e74b8fcacb1cb76a2a83e97c1eb39326fed050cfce5` | `8fd6bd0c8e9107ad2528fe883bb947d3816fc01241454877d050163066c7c2c1` |
| `09-desktop-sessao` | `6b3643932e8fb8a192e6d53857fecee6ba8b04f56200dd23e08d798e791afd93` | `f91ef134506d261f3e2343548c2da3113ca74d15e21718220be73ebcc72712b1` |
| `10-desktop-painel` | `357251081c01a293dafbaaf8d51df1ffb60637b83c990c05f50a2bc08723a8d2` | `9787bbc7e6ade1e6802f3deab3ef9343347ed842157ce820c935a938030014d6` |
| `11-desktop-operacoes` | `c00c9f9c9f3063461fe1ebdc0cf91fd086ab1ef7f43f6987a58d3c4eaecd3899` | `38c18c08f42463b1a9974c93899016c546b754ab285a3abbf02e4c0cc5282f76` |
| `12-mobile-atividade` | `a51007bce479949be1d6b7f8d0b7e0dc6a4c7e725b054833b0ba311a93de5f70` | `9e2ca0744b602c3dc0c8e83608475f9ae680f3b374aedb9b44f31c66261a7066` |
| `13-mobile-salao` | `9c056c25d94c8670c73f6f83dfbf8c110499211ea26b1615eff864d69df30d32` | `ac42e49fd626d6258930cff93d6f0bdc4b7d1d3328649e72d91a4bc4c31cf5eb` |
| `14-mobile-rede` | `85e5cb32ebf55c200f03003e2a1075218a39094e53f797a16534afc469fd2b87` | `1d0c5190718847417461fdb9daf7137748ad748e4effaad6f1832bb6d9a8dc2e` |
| `15-testes` | `712af4454ac42706b080586854979b3ad54b0555d89eb3c71391001350ec0acc` | `65ec010d8d8c649b4eb60266ca7527cfda61fdc841a1097185688a3623acb82d` |
| `20-seq-autenticacao` | `96ff865dcb4609d0f75deee88b57ebf725df1c86f3bd9fb5da96a1dc741b9344` | `b9134347061d68062118d04d2b1897b728dd31d21292880de7cb52e9d74115c8` |
| `21-seq-abertura` | `5c35cca8c2514265b0a9416afc080474c5c2b7f610f33efc00e428084777882b` | `74ba2d8ddd1dda352c8674984196fcde16b07aa801d5661ecb5e993aa164743e` |
| `22-seq-envio-android` | `3327533dffbba9eccdad477e90d170d493122f152df26602304d144758c82025` | `8f7391b69eb43548fcf0f786bc64492ff515f07562751db2920bc85d6d01c45b` |
| `23-seq-registro-pedido` | `9b0eeee80552f7318ac681c05108b047a5f92c6702aac42b5b11990217493562` | `517b77f363eedf514982fd5d42dbeb618c21792273388daab470d0020691baa2` |
| `24-seq-http-mqtt` | `e7f05c26b02132f178adaf0e967b7aa3ea3b5af4535af5c7c3bd3d668d520dab` | `ec4afbf64c4ed330c7d67887b4af33e7ac2ade9c95a7edfad95d380aadfe5ba9` |
| `25-seq-encaminhamento` | `22fd647eecf6a437882c00117262b73be2b96864debcf89fd50a63af89866dcc` | `8551ced2bbd4712b3de889e3eea103c92caf6a2998ca8ac9efe8eff6b8a2c266` |
| `26-seq-preparo-entrega` | `39cb738bef340c4b9cad8730df94a2ff6e6f050886e9a39059413a15dd716f74` | `1d69e9d764ed157a328c7238e8933e1020996d1a6576d8669c4cdd7d459f33e7` |
| `27-seq-impressao-envio` | `4cc69f88c6f1088990f3d4f54d9c2b130d5013a07099889ee177248e00f44fea` | `91670cba23a48350c998ebb932953e13df0b23932ab34022acc4b88f08e081a4` |
| `28-seq-impressao-conferencia` | `4c51802bf87e0e64c322d87e4241cb2f6ae824b19f9c7e34096f9266fe0004d3` | `2b70991b8ad78c6a73e19240f91bb15c460de76a971386097a8e7b13f0318167` |
| `29-seq-pagamento` | `a03204eab97ffc1f897b44482b229af9fa5726996cf90542e35773e880d3c0b6` | `8db22dacb50cc35f213c324e4b5c6b595061d969ea3db09af8daa16bf7e6b0e0` |
| `30-uc-atendimento` | `5b9a0d0d3278d6f8d7b390fa6c4a17eaa389493ac7045b996595d0d660e406f5` | `0d63e828e6b9a4679b8482c6b3b1ab66ff0d3b3269778f9bda0d19fc99a27912` |
| `31-uc-salao-caixa` | `d08db39f12471430da6c8f640ed8f2cf7083e9534340ec36919a6c70c3ac81f2` | `1d0ca09d5352f836a4928007ec8734ee6c4dd2682d38204b0a341215af61d5dd` |
| `32-uc-gerencia-producao` | `d9583a4b5a486a2bef41d7df5038ffba1f9bcbbadf125e118fa46a2a6bac3bec` | `a5565a5319189f5b51b690b0a94ca3b87af88119a799511f7ef99d326b08e280` |
| `33-componentes` | `b7cdb174eb3af78204b3a78dc754cc061effa0dae94fe28f09e018baeb91b8a8` | `1ea1d53250e06bba472894e2e861e04ffb68feddb0017a16da10810c4a691e70` |
| `34-postgresql` | `2cb4b65851c467a2bb84e560e7166d2560cc5eb8e9acacb8e7a716ee948ad05e` | `05c7f27b7bf0715b2070698bf664f013f37030122acd572e57bc2636fa05461c` |
| `35-estados-comanda` | `e5fa9f4d27f99bd210a80d3124c7c47a325676c72e5caef0861152658c343e1f` | `dc31fb9ed4f1334f25e106461fa3ac4fbf4c19543dad20bc73ac13f656483c7f` |
| `36-estados-pedido` | `3f3359b6b482b14a0b570051349e4cbcbbb024bbc21d3f03ceb3ce01753e54d5` | `123817b7f3afcacbbd3bc2f1d2d6fb0d54994468e9ca045661b051e2b390690e` |
| `37-estados-impressao` | `f83fa9e93717e68736f8e3afc0cf83520a16b2a7df398dbfb087b09cb049d00e` | `e6de8e87412c5d1f8ea8696d305b2467d7057b9b43005334403de06a6a3786f2` |

</details>
