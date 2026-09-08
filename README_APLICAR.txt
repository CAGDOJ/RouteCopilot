ROUTECOPILOT - ROMANEIO V2

Substitua/crie estes arquivos dentro do seu projeto atual:

app/src/main/AndroidManifest.xml
app/src/main/java/com/routecopilot/MainActivity.kt
app/src/main/java/com/routecopilot/data/RomaneioModels.kt
app/src/main/java/com/routecopilot/data/RomaneioFolderStore.kt
app/src/main/java/com/routecopilot/data/RomaneioSession.kt
app/src/main/java/com/routecopilot/data/RomaneioRepository.kt
app/src/main/java/com/routecopilot/data/XlsxRomaneioParser.kt
app/src/main/java/com/routecopilot/navigation/WazeLauncher.kt
app/src/main/java/com/routecopilot/spx/SpxBridge.kt
app/src/main/java/com/routecopilot/spx/SpxSessionState.kt
app/src/main/java/com/routecopilot/spx/SpxAccessibilityService.kt
app/src/main/res/xml/accessibility_service_config.xml

NÃO precisa adicionar biblioteca para ler Excel. O parser lê o XLSX diretamente como ZIP/XML.
NÃO precisa de permissão geral de armazenamento. O usuário seleciona Downloads/Romaneio uma vez pelo seletor do Android e o app guarda a permissão.

NOVO FLUXO
1. RouteCopilot > INICIAR ROTA PELO ROMANEIO.
2. Na primeira vez, selecionar a pasta Downloads/Romaneio.
3. O app procura o XLSX mais recente.
4. Lê: AT ID, Sequence, Stop, SPX TN, Destination Address, Bairro, City e Zipcode/Postal code.
5. Ignora Latitude e Longitude existentes no XLSX.
6. A lista fica dentro do RouteCopilot.
7. Cada pedido possui botão NAVEGAR NO WAZE, usando o endereço textual.
8. Quando um novo Romaneio é baixado e o usuário retorna ao Copilot, o app sincroniza o XLSX mais recente.
9. Se for a mesma AT, BRs que existiam antes e sumiram do novo arquivo entram no contador "removidos do romaneio".

SPX
- O romaneio deixa de depender da leitura/rolagem da tela do SPX.
- O botão AUTENTICAR SPX E VOLTAR abre o SPX e ativa uma flag de retorno.
- Se o serviço de Acessibilidade estiver ativo, ao detectar uma tela autenticada/AT, retorna para o RouteCopilot.
- O botão SPX dentro da rota abre o SPX normalmente e NÃO força retorno automático.

COMPILAR
.\gradlew.bat assembleDebug

INSTALAR
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"

LOG SPX
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -c
& "C:\Users\stel-adm\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -s RouteCopilotACC:D
