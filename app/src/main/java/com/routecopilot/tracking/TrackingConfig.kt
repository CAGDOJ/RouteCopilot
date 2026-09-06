package com.routecopilot.tracking

object TrackingConfig {

    /*
     * Deixe vazio para usar apenas mapa/ETA/mensagem local.
     *
     * Para o link do cliente funcionar na internet, publique a pasta
     * backend/ deste pacote e coloque aqui o endereço HTTPS, sem barra final.
     *
     * Exemplo:
     * const val BASE_URL = "https://tracking.seudominio.com"
     */
    const val BASE_URL = ""

    const val DEFAULT_SERVICE_SECONDS = 120L
}
