# RouteCopilot V4

Atualização focada no fluxo aprovado para operação em uma tela.

## O que foi alterado

### Home
- tela inicial limpa;
- somente o estado do SPX;
- botão **SINCRONIZAR**;
- **CONTINUAR ROTA** quando existe rota salva;
- última sincronização;
- sem mapa antes de sincronizar.

### Romaneio
- lê a pasta selecionada pelo Android Storage Access Framework;
- considera `DATA + AT` como identidade da rota;
- mesma data + mesma AT: mantém apenas o XLSX mais recente;
- mesma data + AT diferente: mantém rotas separadas;
- suporta XLSX contendo mais de uma AT;
- BR duplicado dentro do arquivo é deduplicado;
- latitude/longitude do XLSX não são usadas;
- nome e telefone só são lidos de colunas próprias quando elas existirem;
- telefone nunca é extraído da linha inteira, evitando misturar data/AT no WhatsApp.

### Pedidos e paradas
- `Nome`, `End` e `Bairro` exibidos em linhas separadas;
- pedidos são agrupados por endereço para formar paradas físicas;
- pedido e parada são contagens diferentes;
- ocorrências permanecem na rota e no mapa;
- possíveis ocorrências também permanecem visíveis.

### Mapa
- mini mapa dentro da tela da rota;
- toque em **Expandir** para tela cheia;
- zoom + / - nativo do mapa;
- pinch zoom e arrastar com o dedo;
- marcadores numerados;
- ocorrência em laranja;
- geocodificação usa endereço/bairro/cidade/CEP, não as coordenadas do XLSX;
- cache de endereços para as próximas sincronizações ficarem mais rápidas.

### Operação
- **INICIAR ENTREGAS** muda o estado da mesma tela;
- ícone `📷` fica disponível durante a rota;
- leitor de código usa Google Code Scanner;
- bipar identifica o BR, sem marcar automaticamente como entregue;
- após bipar: **ENTREGUE**, **OCORRÊNCIA** ou **CONFIRMAR NO SPX**;
- **PAUSAR / RETOMAR** com motivos;
- rota e estados persistem para **CONTINUAR ROTA**.

### SPX
- SPX não é usado para importar o romaneio;
- status simples: conectado / autenticação necessária / indisponível;
- `ENCERRADO` é tratado como tela autenticada;
- a flag de retorno é limpa antes de voltar ao Copilot para evitar loop;
- o SPX só é aberto quando necessário para autenticação ou ação oficial.

### Portal do cliente
A pasta `client_portal/` contém o módulo web aprovado:
- página em `100dvh`, sem rolagem;
- BR visível;
- Em mãos / Vizinho / Portaria / Varanda-Caixa de correio / Ninguém para receber;
- Vizinho: nome + contato;
- Tem palavra-chave? SIM/NÃO; se SIM aparece `Qual?`;
- mapa ao vivo com carro laranja + caixa laranja;
- ao confirmar, só o botão muda para `✓ PEDIDO CONFIRMADO`;
- `Ninguém para receber` fica disponível para o Copilot tratar como possível ocorrência.

> O portal precisa ser publicado em um servidor HTTPS para virar um link público real.

## WhatsApp sem alternância de telas

Não foi implementado envio silencioso fingindo que o WhatsApp comum enviou uma mensagem. Para exibir estados reais `enviando / enviada / entregue / lida / falhou` sem abrir o WhatsApp, é necessária a API oficial do WhatsApp Business no backend. A V4 evita criar uma automação frágil de interface que abriria várias abas.

## Aplicar sobre o repositório atual

Extraia o ZIP e, dentro da pasta extraída, execute:

```powershell
.\APLICAR_ATUALIZACAO.ps1 -ProjectPath "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"
```

Depois:

```powershell
cd C:\Users\stel-adm\Documents\GitHub\RouteCopilot
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

Instalação:

```powershell
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## Observação sobre o XLSX enviado para teste

O arquivo de exemplo enviado possui as colunas `AT ID`, `Sequence`, `Stop`, `SPX TN`, `Destination Address`, `Bairro`, `City`, `Zipcode/Postal code`, `Latitude` e `Longitude`.
Ele não contém colunas de nome do cliente nem telefone. Por isso esta versão não inventa esses dados e não tenta extraí-los de outros campos.
