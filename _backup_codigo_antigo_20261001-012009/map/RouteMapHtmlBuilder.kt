package com.routecopilot.map

import com.routecopilot.model.ImportedRoute

object RouteMapHtmlBuilder {

    fun build(
        route: ImportedRoute
    ): String {

        /*
         * Apenas pedidos que possuem coordenadas
         * entram no mapa.
         *
         * A quantidade TOTAL de pacotes continua
         * independente da quantidade mapeada.
         */
        val mapped =
            route.packages
                .filter { item ->

                    item.latitude != null &&
                        item.longitude != null
                }
                .sortedWith(

                    compareBy(
                        { item ->

                            /*
                             * Prioridade:
                             *
                             * 1. Stop
                             * 2. Sequence
                             * 3. Linha do Excel
                             */
                            item.stop
                                ?: item.sequence
                                ?: Int.MAX_VALUE
                        },
                        { item ->

                            item.sourceRow
                        }
                    )
                )

        /*
         * Constrói os objetos JavaScript.
         */
        val markers =
            mapped
                .mapIndexed { index, item ->

                    /*
                     * Número mostrado no marcador.
                     */
                    val mapNumber =
                        item.stop
                            ?: item.sequence
                            ?: (index + 1)

                    """
                    {
                        lat: ${item.latitude},
                        lng: ${item.longitude},
                        br: ${js(item.br)},
                        sequence: ${item.sequence ?: "null"},
                        stop: ${item.stop ?: "null"},
                        number: $mapNumber,
                        address: ${js(item.fullAddress)}
                    }
                    """.trimIndent()
                }
                .joinToString(
                    ",\n"
                )

        /*
         * Não usamos ${'$'}{...} no JavaScript.
         *
         * Usamos concatenação JS normal para impedir
         * o Kotlin de tentar interpretar variáveis
         * JavaScript como variáveis Kotlin.
         */
        return """
<!DOCTYPE html>

<html lang="pt-BR">

<head>

    <meta charset="UTF-8">

    <meta
        name="viewport"
        content="width=device-width,
        initial-scale=1.0,
        maximum-scale=1.0,
        user-scalable=no"
    >

    <link
        rel="stylesheet"
        href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    >

    <script
        src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
    </script>

    <style>

        html,
        body {

            width: 100%;
            height: 100%;

            margin: 0;
            padding: 0;

            background: #08111F;

            overflow: hidden;
        }

        #map {

            width: 100%;
            height: 100%;

            background: #08111F;
        }

        .leaflet-container {

            background: #08111F;

            font-family:
                Arial,
                Helvetica,
                sans-serif;
        }

        .leaflet-popup-content-wrapper {

            background: #111C2E;

            color: #F8FAFC;

            border-radius: 12px;
        }

        .leaflet-popup-tip {

            background: #111C2E;
        }

        .leaflet-popup-content {

            margin: 13px 15px;

            line-height: 1.5;
        }

        .route-badge {

            width: 34px;
            height: 34px;

            display: flex;

            align-items: center;
            justify-content: center;

            border-radius: 50%;

            background: #F97316;

            color: #FFFFFF;

            border: 2px solid #FFFFFF;

            font-weight: 800;

            font-size: 12px;

            box-shadow:
                0 3px 8px
                rgba(0, 0, 0, 0.42);
        }

        .popup-br {

            font-weight: 800;

            color: #38BDF8;
        }

        .popup-address {

            margin-top: 6px;

            color: #F8FAFC;
        }

        .popup-info {

            margin-top: 4px;

            color: #94A3B8;
        }

    </style>

</head>

<body>

<div id="map"></div>

<script>

    /*
     * Dados preparados pelo Kotlin.
     */
    const points = [
$markers
    ];


    /*
     * Inicializa o mapa.
     */
    const map =
        L.map(
            "map",
            {
                zoomControl: true,
                attributionControl: true
            }
        );


    /*
     * OpenStreetMap.
     */
    L.tileLayer(
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        {
            maxZoom: 19,

            attribution:
                "&copy; OpenStreetMap contributors"
        }
    ).addTo(
        map
    );


    /*
     * Se nenhum endereço tiver coordenadas,
     * abre uma visão padrão de Belém.
     */
    if (
        points.length === 0
    ) {

        map.setView(
            [
                -1.4558,
                -48.4902
            ],
            12
        );

    } else {

        const bounds = [];

        const routeLine = [];


        /*
         * Cria todos os pontos.
         */
        points.forEach(
            function(
                p,
                index
            ) {

                /*
                 * IMPORTANTE:
                 *
                 * Não usamos template literal ${'$'}{...}
                 * aqui porque esse HTML está dentro
                 * de uma String Kotlin.
                 */
                const markerHtml =
                    '<div class="route-badge">' +
                    String(
                        p.number
                    ) +
                    '</div>';


                const icon =
                    L.divIcon(
                        {

                            className:
                                "",

                            html:
                                markerHtml,

                            iconSize:
                                [
                                    34,
                                    34
                                ],

                            iconAnchor:
                                [
                                    17,
                                    17
                                ],

                            popupAnchor:
                                [
                                    0,
                                    -17
                                ]
                        }
                    );


                const marker =
                    L.marker(
                        [
                            p.lat,
                            p.lng
                        ],
                        {
                            icon: icon
                        }
                    )
                    .addTo(
                        map
                    );


                /*
                 * Popup.
                 */
                let info =
                    '<div class="popup-br">' +
                    escapeHtml(
                        p.br
                    ) +
                    '</div>';


                if (
                    p.stop !== null
                ) {

                    info +=
                        '<div class="popup-info">' +
                        'Parada: ' +
                        escapeHtml(
                            String(
                                p.stop
                            )
                        ) +
                        '</div>';
                }


                if (
                    p.sequence !== null
                ) {

                    info +=
                        '<div class="popup-info">' +
                        'Sequência SPX: ' +
                        escapeHtml(
                            String(
                                p.sequence
                            )
                        ) +
                        '</div>';
                }


                if (
                    p.address
                ) {

                    info +=
                        '<div class="popup-address">' +
                        escapeHtml(
                            p.address
                        ) +
                        '</div>';
                }


                marker.bindPopup(
                    info
                );


                /*
                 * Guarda o ponto para ajustar a câmera.
                 */
                bounds.push(
                    [
                        p.lat,
                        p.lng
                    ]
                );


                /*
                 * Guarda a sequência para desenhar
                 * a linha entre os pontos.
                 */
                routeLine.push(
                    [
                        p.lat,
                        p.lng
                    ]
                );
            }
        );


        /*
         * Linha mostrando a ordem atual.
         *
         * Depois o motor de otimização poderá
         * substituir essa sequência pela rota
         * calculada pelo RouteCopilot.
         */
        if (
            routeLine.length > 1
        ) {

            L.polyline(
                routeLine,
                {
                    weight: 4,
                    opacity: 0.72
                }
            )
            .addTo(
                map
            );
        }


        /*
         * Enquadra todos os pedidos no mapa.
         */
        if (
            bounds.length === 1
        ) {

            map.setView(
                bounds[0],
                16
            );

        } else {

            map.fitBounds(
                bounds,
                {
                    padding:
                        [
                            35,
                            35
                        ],

                    maxZoom:
                        17
                }
            );
        }
    }


    /*
     * Impede endereço/BR de inserir HTML
     * dentro do popup.
     */
    function escapeHtml(
        value
    ) {

        return String(
            value || ""
        )
        .replace(
            /&/g,
            "&amp;"
        )
        .replace(
            /</g,
            "&lt;"
        )
        .replace(
            />/g,
            "&gt;"
        )
        .replace(
            /"/g,
            "&quot;"
        )
        .replace(
            /'/g,
            "&#039;"
        );
    }

</script>

</body>

</html>
        """.trimIndent()
    }


    /*
     * Converte String Kotlin para String
     * JavaScript segura.
     */
    private fun js(
        value: String
    ): String {

        val escaped =
            value
                .replace(
                    "\\",
                    "\\\\"
                )
                .replace(
                    "\"",
                    "\\\""
                )
                .replace(
                    "\r",
                    ""
                )
                .replace(
                    "\n",
                    "\\n"
                )
                .replace(
                    "</",
                    "<\\/"
                )

        return "\"$escaped\""
    }
}