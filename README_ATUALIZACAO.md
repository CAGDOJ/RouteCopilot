# RouteCopilot - Atualização de sincronização SPX

Arquivos desta atualização:

- app/src/main/java/com/routecopilot/data/model/Delivery.kt
- app/src/main/java/com/routecopilot/data/repository/RouteRepository.kt
- app/src/main/java/com/routecopilot/spx/SpxParser.kt
- app/src/main/java/com/routecopilot/spx/SpxSessionState.kt
- app/src/main/java/com/routecopilot/spx/SpxAccessibilityService.kt
- app/src/main/res/xml/accessibility_service_config.xml

Objetivo:
- manter o SPX como fonte oficial;
- importar rota e BRs;
- continuar monitorando o SPX depois da importação;
- refletir status finais detectados no SPX;
- tentar coletar nome/endereço/bairro/telefone quando a tela expõe um único BR;
- retornar ao RouteCopilot após sincronização inicial e após baixa detectada.

Compilar:
.\gradlew.bat assembleDebug

Instalar:
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"

Log:
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -c
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s RouteCopilotACC:D
