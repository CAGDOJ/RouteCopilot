# RouteCopilot FINAL V4

Esta versão consolida o fluxo do SPX, cancelamento, mapa interno, página web do cliente e disparo de mensagens.

## Corrigido agora

### Cancelar sincronização preso no SPX
Durante a sincronização aparece `← VOLTAR / CANCELAR` por cima do SPX. Ao tocar, o serviço:

1. cancela callbacks;
2. zera o estado da sincronização;
3. força HOME;
4. traz o RouteCopilot para frente;
5. faz uma segunda tentativa se o SPX continuar visível.

Logs: `IMPORT=CANCELLED` e, se necessário, `CANCEL_RETURN=FALLBACK`.

### Mapa do cliente
O link individual abre uma página web semelhante à referência:

- mapa em tela cheia;
- entregador = **caixa laranja**;
- cliente = marcador preto;
- painel inferior branco;
- previsão `Chegará antes das HHhMM`;
- quantidade de paradas restantes;
- atualização automática a cada 5 segundos;
- o horário muda na página, não precisa mandar nova mensagem;
- quando possível, desenha o trajeto pelas ruas entre entregador e cliente.

O cliente vê apenas a posição do entregador e a própria parada.

### Mensagem de início para todos
Há um botão `DISPARAR MENSAGEM DE INÍCIO PARA TODOS`. Um toque inicia uma fila de todos os clientes com telefone identificado. Cada cliente recebe seu link individual.

**WhatsApp comum:** o Android/WhatsApp não permite que um app externo confirme silenciosamente o envio de dezenas de mensagens. A fila abre cada conversa com a mensagem pronta; você confirma e, ao voltar, a próxima abre automaticamente. Para envio realmente silencioso em lote é necessária a API oficial WhatsApp Business/Cloud API.

### Pedidos
Na lista principal o foco é:

- NOME
- ENDEREÇO
- BAIRRO

O BR fica como identificador interno.

### SPX
Também contém:

- copiar AT;
- Em Rota;
- Ocorrências com espera para a aba não ser considerada `0` antes de carregar;
- descrições de ocorrência;
- Encerrados do dia;
- retorno automático ao RouteCopilot.

## Para o link web funcionar
A pasta `backend/` precisa estar publicada em uma URL HTTPS. Depois configure:

```powershell
powershell.exe -ExecutionPolicy Bypass -File ".\CONFIGURAR_LINK.ps1" -BaseUrl "https://SEU-ENDERECO-HTTPS"
```

Sem backend público, o mapa interno funciona, mas o cliente não consegue abrir um link público de rastreamento.

## Aplicar

```powershell
powershell.exe -ExecutionPolicy Bypass -File ".\APLICAR_CORRECAO.ps1" -ProjectRoot "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"
.\gradlew.bat assembleDebug
```

Depois:

```powershell
$adb = "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

Log:

```powershell
& $adb logcat -c
& $adb logcat -s RouteCopilotACC:D
```

## Fluxo ideal do aplicativo

1. INICIAR ROTA.
2. Confere acessibilidade, GPS e internet.
3. Abre SPX.
4. Login/aceite/facial continuam manuais e oficiais no SPX.
5. Lê AT.
6. Lê Em Rota.
7. Importa nome/endereço/bairro/telefone.
8. Lê Ocorrências e Encerrados.
9. Volta ao RouteCopilot.
10. Geocodifica e valida endereços.
11. Otimiza sequência.
12. Mostra mapa RouteCopilot.
13. Dispara mensagem inicial para todos.
14. Inicia rastreamento.
15. ETA e número de paradas se atualizam no link web.
16. A cada entrega/ocorrência, reotimiza a rota e recalcula ETA.
17. No fim do dia mostra resumo operacional.

## Próximas melhorias que valem a pena

- Correção rápida de endereço sem número/ambíguo.
- Detecção de endereços duplicados e condomínios para agrupar paradas.
- Detector de pacote fora do cluster da rota.
- Reotimização automática após Entregue/Ocorrência.
- Cronômetro real por entrega para aprender o tempo médio do entregador.
- Histórico diário com km, tempo, entregues, ocorrências e motivos.
- Retomar rota após reiniciar o celular/app.
- Indicador GPS/rede/bateria antes de começar.
- Expiração automática dos links do cliente no fim do dia.
- Backend autenticado e HTTPS próprio.
- Roteador próprio (OSRM/Valhalla) para não depender de serviço público.
- Modo offline para dados da rota e cache do mapa.
- Tela de exceções: endereço inválido, sem número, telefone ausente, geocodificação duvidosa.
- Comparativo SPX x RouteCopilot em km/tempo depois de calcular os dois trajetos.
