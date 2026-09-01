package com.routecopilot.model

data class DeliveryPackage(

    val br: String,

    val at: String? = null,

    /*
     * Sequence vem diretamente do Excel SPX.
     */
    val sequence: Int? = null,

    /*
     * Stop também vem diretamente do Excel.
     * Ele pode representar o ponto/parada da entrega.
     */
    val stop: Int? = null,

    val recipient: String? = null,

    val phone: String? = null,

    val address: String = "",

    val neighborhood: String? = null,

    val city: String? = null,

    val state: String? = null,

    val zipCode: String? = null,

    val status: String? = null,

    /*
     * O Excel que você enviou já possui
     * Latitude e Longitude.
     */
    val latitude: Double? = null,

    val longitude: Double? = null,

    /*
     * Linha original dentro do Excel.
     */
    val sourceRow: Int = 0
) {

    val fullAddress: String
        get() {

            return listOfNotNull(

                address
                    .takeIf {
                        it.isNotBlank()
                    },

                neighborhood
                    ?.takeIf {
                        it.isNotBlank()
                    },

                city
                    ?.takeIf {
                        it.isNotBlank()
                    },

                state
                    ?.takeIf {
                        it.isNotBlank()
                    },

                zipCode
                    ?.takeIf {
                        it.isNotBlank()
                    }

            ).joinToString(", ")
        }
}

data class ImportedRoute(

    val at: String? = null,

    val sourceFileName: String? = null,

    val packages: List<DeliveryPackage> =
        emptyList()
) {

    /*
     * ESTE é o número real de pacotes.
     *
     * Não depende do mapa.
     */
    val packageCount: Int
        get() =
            packages.size

    /*
     * Quantos possuem coordenadas.
     */
    val mappedCount: Int
        get() =
            packages.count {

                it.latitude != null &&
                    it.longitude != null
            }

    /*
     * Quantos ainda não conseguiram ser colocados
     * no mapa.
     */
    val unmappedCount: Int
        get() =
            packageCount -
                mappedCount
}