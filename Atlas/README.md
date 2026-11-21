# ATLAS

Projeto da disciplina de Programação Orientada a Objetos da UFG. O garçom abre comandas e registra pedidos no Android. No Desktop, a gerência mantém os cadastros, acompanha os pedidos e envia os comprovantes para as filas do bar e da cozinha. Gerente e caixa recebem pagamentos. O Backend verifica as permissões e registra as operações no PostgreSQL.

| Pasta | Conteúdo |
|---|---|
| `Modelo/` | Classes de domínio e testes em memória |
| `Backend/` | API HTTP, sessões, persistência JDBC e publicação MQTT |
| `Desktop/` | Aplicação JavaFX com FXML, CSS e Java Print Service |
| `Mobile/` | Aplicação Android em Java com layouts XML |
| `BancoDeDados/` | Esquema PostgreSQL |

## Executar o Backend e o Desktop

Na raiz do projeto, compile os módulos Maven com JDK 17 ou superior.

```powershell
mvn install
```

Em uma instalação nova de PostgreSQL, crie o papel e o banco usados pelo ATLAS. Neste exemplo, `postgres` é o administrador. Aplique o esquema uma vez, conectado como o usuário que o Backend vai utilizar.

```powershell
createuser -h localhost -U postgres --pwprompt atlas
createdb -h localhost -U postgres --owner atlas atlas
psql -h localhost -U atlas -d atlas -v ON_ERROR_STOP=1 -f BancoDeDados/esquema.sql
psql -h localhost -U atlas -d atlas -v ON_ERROR_STOP=1 -f BancoDeDados/001_importacao_e_operacoes_em_massa.sql
```

Em um banco existente, aplique somente `001_importacao_e_operacoes_em_massa.sql` uma vez, antes de iniciar o Backend atualizado.

O Backend usa `ATLAS_BANCO_URL` (padrão `jdbc:postgresql://localhost:5432/atlas`) e `ATLAS_BANCO_USUARIO` (padrão `atlas`). Defina `ATLAS_BANCO_SENHA` no terminal se o banco exigir senha. Em PowerShell, use a forma `$env:ATLAS_BANCO_USUARIO = 'atlas'` para definir uma variável de ambiente.

Na raiz do projeto, em um terminal interativo, cadastre o primeiro gerente. O comando pede código, nome e senha e exige que a tabela de funcionários esteja vazia.

```powershell
mvn -pl Backend exec:java '-Dexec.args=criar-gerente'
```

Para habilitar os avisos de atualização, disponibilize um broker Mosquitto acessível pelo Backend e pelos clientes. O Backend usa `ATLAS_MQTT_URL` (padrão `tcp://localhost:1883`). Dê ao Backend permissão para publicar em `atlas/eventos` e aos clientes permissão para assinar esse tópico. Use `ATLAS_MQTT_USUARIO` e `ATLAS_MQTT_SENHA` no Backend e no Desktop quando o broker exigir autenticação.

Inicie o Backend no terminal em que configurou o banco. O servidor HTTP escuta em `0.0.0.0:8080` por padrão. Ajuste a interface e a porta com `ATLAS_HTTP_HOST` e `ATLAS_HTTP_PORTA`.

```powershell
mvn -pl Backend exec:java
```

Em outro terminal, na mesma raiz, inicie o Desktop.

```powershell
mvn -pl Desktop javafx:run
```

Use **Configurar conexão** na tela de login do Desktop se o Backend estiver em outro computador. O endereço padrão é `http://localhost:8080`. Entre como gerente para cadastrar mesas, produtos e funcionários. Em **Configurações**, selecione as filas de impressão do sistema operacional para o bar e a cozinha.

## Gerar e instalar o APK de debug

Use JDK 17 ou 21, plataforma Android 35 e SDK Build Tools 34.0.0. O projeto usa Android Gradle Plugin 8.7.3 e o Wrapper baixa o Gradle 8.9. O requisito mínimo do aplicativo é Android 8.0 (API 26). [Compatibilidade do Android Gradle Plugin](https://developer.android.com/build/releases/agp-8-7-0-release-notes)

Instale as ferramentas no SDK Manager e abra `Mobile/` no Android Studio. Para gerar o APK no terminal, execute na pasta `Mobile`, com o JDK no `PATH` ou em `JAVA_HOME`.

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK com assinatura de debug fica em `Mobile/app/build/outputs/apk/debug/app-debug.apk`. Para instalar pelo Android Studio, conecte o aparelho com depuração USB habilitada, autorize a conexão e use **Run**. Para instalar pelo gerenciador de arquivos, copie o APK para o aparelho e autorize a instalação por esse aplicativo se o Android solicitar. Para instalar uma atualização sobre um APK já distribuído, use a mesma chave de assinatura. Para distribuir o APK de teste em uma Release, gere-o a partir do commit da tag e anexe-o como artefato da Release. O Wrapper fica no projeto. APKs, saídas de compilação, `local.properties` e chaves de assinatura são arquivos locais.

No login do Android, abra **⋮ > Configurações de rede**, informe a URL do Backend com o IP ou nome do computador na LAN e a porta, por exemplo `http://192.168.1.10:8080`. Toque em **Salvar** e entre com o código e a senha de um garçom ativo. O Backend serve HTTP. Para informar uma URL HTTPS nos clientes, disponibilize um endereço com terminação TLS à frente do Backend.

Por padrão, Desktop e Android procuram o broker no host da API, na porta 1883. Ajuste o endereço no Desktop com `ATLAS_MQTT_URL`. No Android, o endereço e as credenciais do broker ficam no menu **Configurações de rede**. A senha MQTT do Android é mantida em memória. Com Backend e Desktop no mesmo computador, só o Android usa o endereço da LAN. Se o Desktop estiver em outro computador, configure esse endereço nos dois clientes.

## Operação

- Em **Cardápio** e **Funcionários**, o gerente pode importar CSV, desativar todos ou excluir todos. A importação valida o arquivo e mostra a quantidade antes da confirmação. A exclusão é definitiva e preserva o histórico. Nas ações sobre funcionários, a conta do gerente que executa a operação permanece ativa. Resolva as comandas e operações indicadas pelo sistema antes de repetir uma exclusão bloqueada. Reative individualmente uma conta se ela precisar voltar a operar.
- O Android guarda o conteúdo e a chave do pedido antes de enviá-lo. Se o resultado do envio for inconclusivo, repita com o mesmo rascunho para evitar um novo pedido.
- Na área **Produção**, a gerência envia manualmente os comprovantes por setor. O estado `ENVIADA` indica que o envio à fila foi aceito ou confirmado pelo operador. Confira a fila antes de liberar nova tentativa após um resultado incerto.
- O caixa ou gerente recebe a conta solicitada pelo garçom. As transições, os perfis e as regras de pagamento estão no [contrato da API](Documentacao/API.md).

Cancelamento, estorno, transferência, gorjeta, pagamento parcial, relatórios e higienização estão fora do escopo operacional atual.

### Arquivos CSV

Salve o arquivo em UTF-8, com separador `;`. O BOM é aceito. Use aspas em campos com ponto e vírgula ou quebra de linha e escreva aspas internas como `""`. Use `SIM` e `NÃO` para as colunas de estado. Os limites são 512 KiB por arquivo, 1.000 produtos ou 100 funcionários por importação.

**Cardápio**. A categoria precisa existir. O cadastro individual de produto permite criar a categoria. O preço aceita vírgula ou ponto decimal, com até duas casas. Descrição e imagem são opcionais e, quando preenchida, a imagem precisa ser uma URL HTTPS.

```csv
nome;descricao;preco;categoria;setor;imagem;disponivel
X-Burger;"Pão; carne e queijo";29,90;Hambúrgueres;COZINHA;;SIM
```

**Funcionários**. O expediente é opcional. A senha inicial deve ter de 8 a 128 caracteres. Proteja o CSV, pois ele contém credenciais iniciais. Os valores `BAR` e `COZINHA` correspondem aos cargos existentes `BARMAN` e `COZINHEIRO`.

```csv
username;name;role;schedule;password;active
ana_silva;Ana Silva;GARCOM;18h às 00h;SenhaTemporaria123;SIM
```

## Abrir o código

- **BlueJ**. Em **Project > Open Non BlueJ Project**, abra `Modelo/src/main/java`. As classes estão no pacote `br.ufg.atlas.modelo`. `Restaurante` mantém as mesas e o cardápio.
- **Scene Builder**. Abra os FXML em `Desktop/src/main/resources/desktop`. `Login.fxml` é a entrada, `Painel.fxml` contém a navegação e `Inicio.fxml` é a tela inicial. Os controladores ficam no pacote `br.ufg.atlas.desktop`.
- **Referências de interface**. [Marca e login](https://www.figma.com/design/XpiRrMdVxpX7dMNIAYdMzI/Atlas?node-id=167-2) · [Desktop](https://www.figma.com/design/qn1c28a2EVnptWWzrP09xT/Atlas-Desktop) · [Mobile](https://www.figma.com/design/MGn27dMT2lV4oWScmpdZQX/ATLAS-Mobile-%E2%80%94-Gar%C3%A7om)

## Documentação

- [API HTTP e MQTT](Documentacao/API.md)
- [Verificações, comandos de teste e pendências](Documentacao/Verificacao.md)
- [Diagramas UML](../ATLAS-Documentacao/UML/README.md)
