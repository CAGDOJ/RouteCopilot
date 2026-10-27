ROUTECOPILOT FIX V2.1
=====================

Este patch corrige o erro de compilacao causado por arquivos Kotlin antigos
que permaneceram no projeto depois da atualizacao V2.

O log mostrou que o Gradle ainda compilava classes antigas como:
- map/RouteMapScreen.kt
- messaging/CustomerMessaging.kt
- route/EtaEngine.kt
- route/GeocodingManager.kt
- route/RouteOptimizer.kt
- scanner/PackageScanner.kt
- spx/SpxBridge.kt
- tracking/CourierTrackingService.kt
- tracking/TrackingClient.kt

Esses arquivos pertencem a uma arquitetura anterior e dependem de modelos
que nao existem mais na V2, como DeliveryStop, SpxStatus, stops,
trackingToken e markMessageSent.

O script:
1. cria backup dos arquivos antigos;
2. remove apenas os modulos legados que conflitam;
3. executa gradlew clean;
4. executa gradlew assembleDebug;
5. informa o caminho do APK se o build passar.

COMO USAR
---------

1. Extraia RouteCopilot_FIX_V2_1.zip.
2. Abra PowerShell na raiz do projeto:

   C:\Users\stel-adm\Documents\GitHub\RouteCopilot

3. Rode:

powershell -ExecutionPolicy Bypass -File "C:\Users\stel-adm\Downloads\RouteCopilot_FIX_V2_1\CORRIGIR_BUILD.ps1"

Se sua pasta estiver em outro local, ajuste apenas o caminho do script.
