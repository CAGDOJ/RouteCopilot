# RouteCopilot FINAL V3

Esta atualização fecha os ajustes de **cancelamento da sincronização, mapa web do cliente e mensagens**.

## 1. Cancelar enquanto o SPX está aberto

Durante a sincronização aparece sobre o SPX um botão pequeno:

`← VOLTAR / CANCELAR`

Ao tocar nele:

- a leitura é cancelada;
- callbacks de sincronização são interrompidos;
- o estado volta para IDLE;
- o RouteCopilot abre novamente na tela inicial.

Também foi corrigido o estado `IDLE` para sempre retornar à HOME.

## 2. Página web do cliente

A página foi refeita no padrão da referência enviada:

- mapa em tela cheia;
- entregador representado por **caixa laranja**;
- endereço do cliente com marcador preto;
- painel branco inferior;
- texto verde de situação;
- `Chegará antes das HHhMM`;
- `Seu endereço é a próxima parada` quando for a próxima;
- caso contrário mostra quantas paradas faltam;
- atualização automática a cada 5 segundos.

O horário **não vai mais fixo no WhatsApp**. Ele muda na página web conforme a rota muda.

Cada link continua exclusivo para um pedido. O cliente não recebe a rota completa nem vê outros endereços.

## 3. Mensagens para todos

Na tela da rota existe agora:

`DISPARAR MENSAGEM DE INÍCIO PARA TODOS`

E na tela de mensagens:

`DISPARAR MENSAGEM PARA TODOS`

O RouteCopilot inicia uma fila com uma mensagem e link individual para cada telefone.

### Limite do WhatsApp comum

O WhatsApp comum não oferece uma API pública para um aplicativo externo apertar **Enviar** silenciosamente para vários contatos.

Por isso o RouteCopilot faz o máximo permitido sem usar automação insegura:

1. um toque inicia a fila inteira;
2. abre o primeiro cliente com a mensagem pronta;
3. você confirma o envio;
4. ao voltar ao RouteCopilot, o próximo cliente abre automaticamente;
5. repete até terminar.

Para envio realmente silencioso em lote seria necessário usar a API oficial do WhatsApp Business.

## 4. Mensagem

A mensagem inicial não leva mais um ETA que pode ficar errado. Ela leva o link de acompanhamento.

O ETA, posição e quantidade de paradas restantes ficam atualizando na página web.

## 5. Publicar o rastreamento

A pasta `backend` contém o servidor FastAPI pronto e um `Dockerfile`.

Depois que ele estiver publicado em uma URL HTTPS, configure o aplicativo:

```powershell
powershell.exe -ExecutionPolicy Bypass -File ".\CONFIGURAR_LINK.ps1" -BaseUrl "https://SEU-ENDERECO-HTTPS"
```

Depois recompile e instale o APK.

## Aplicar no projeto

```powershell
powershell.exe -ExecutionPolicy Bypass -File ".\APLICAR_CORRECAO.ps1" -ProjectRoot "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"
```

Compile:

```powershell
.\gradlew.bat assembleDebug
```

Instale:

```powershell
$adb = "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

Log SPX:

```powershell
& $adb logcat -c
& $adb logcat -s RouteCopilotACC:D
```

Novos logs do cancelamento:

```text
SYNC_OVERLAY=SHOW
IMPORT=CANCELLED
```
