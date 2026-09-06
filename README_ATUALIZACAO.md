# RouteCopilot — atualização consolidada

Esta atualização consolida:

- sincronização SPX por AccessibilityService;
- detecção de login, aceite e reconhecimento facial sem automatizar credenciais;
- detecção de AT;
- leitura e deduplicação de BRs;
- leitura heurística de endereço/telefone/destinatário dos cartões visíveis;
- rolagem automática para importar a lista;
- retorno ao RouteCopilot ao terminar;
- mapa da rota dentro do RouteCopilot (sem Waze obrigatório);
- geocodificação dos endereços;
- otimização simples por vizinho mais próximo;
- ETA incluindo deslocamento + tempo médio gasto em cada entrega anterior;
- mensagem ao cliente seguindo o padrão:
  "Olá! Sua encomenda está em rota de entrega. Você poderá acompanhar a aproximação do entregador pelo link abaixo.";
- botão de envio via WhatsApp/compartilhamento;
- backend opcional de rastreamento por link exclusivo de parada.

## IMPORTANTE SOBRE O LINK DO CLIENTE

Mapa, otimização, ETA e mensagem funcionam no aplicativo sem backend.

Para o link público do cliente mostrar a posição do entregador e somente a parada daquele pedido,
é necessário publicar a pasta `backend/` em um servidor HTTPS e preencher:

`app/src/main/java/com/routecopilot/tracking/TrackingConfig.kt`

com:

`const val BASE_URL = "https://seu-servidor"`

Sem isso, a mensagem é enviada com o ETA, mas sem link de rastreamento.

## Aplicar

1. Extraia este ZIP.
2. No PowerShell, entre na pasta do seu projeto:

   `cd C:\Users\stel-adm\Documents\GitHub\RouteCopilot`

3. Rode o script do pacote, apontando para o projeto:

   `powershell -ExecutionPolicy Bypass -File "CAMINHO\RouteCopilot_FULL_UPDATE\APLICAR_UPDATE.ps1" -ProjectRoot "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"`

4. Compile:

   `.\gradlew.bat assembleDebug`

5. Instale:

   `& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"`

6. Log:

   `& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -c`

   `& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s RouteCopilotACC:D`

## Backend local para teste

Na pasta backend:

`python -m venv .venv`

`.venv\Scripts\activate`

`pip install -r requirements.txt`

`uvicorn main:app --host 0.0.0.0 --port 8000`

Para cliente externo, localhost não serve. É preciso publicar em um endereço HTTPS acessível pela internet.

## Observação

O mapa interno usa Leaflet + OpenStreetMap em WebView.
O Waze não é necessário.
