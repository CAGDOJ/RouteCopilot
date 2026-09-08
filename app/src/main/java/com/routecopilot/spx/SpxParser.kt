package com.routecopilot.spx

import com.routecopilot.data.model.DeliveryStatus

data class SpxScreenSnapshot(
    val at: String? = null,
    val expectedTotal: Int? = null,
    val trackingCodes: Set<String> = emptySet(),
    val currentTrackingCode: String? = null,
    val customerName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val status: DeliveryStatus? = null,
    val looksLikeLogin: Boolean = false,
    val looksAuthenticated: Boolean = false
)

object SpxParser {

    private val atRegex =
        Regex("""\bAT[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    private val brRegex =
        Regex("""\bBR[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    private val fractionRegex =
        Regex("""(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)""")

    private val totalTextRegex =
        Regex(
            """(?<!\d)(\d{1,4})\s*(?:pedidos?|pacotes?|entregas?)(?!\w)""",
            RegexOption.IGNORE_CASE
        )

    private val phoneRegex =
        Regex("""(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?\d{4,5}[-\s]?\d{4}""")

    fun parse(
        textos: List<String>
    ): SpxScreenSnapshot {

        val cleaned =
            textos
                .map { it.trim() }
                .filter { it.isNotBlank() }

        val allText =
            cleaned.joinToString(" ")

        val lower =
            allText.lowercase()

        val codigos =
            linkedSetOf<String>()

        cleaned.forEach { texto ->

            val normalizado =
                texto
                    .replace(" ", "")
                    .replace("\n", "")
                    .uppercase()

            brRegex
                .findAll(normalizado)
                .forEach {
                    codigos.add(
                        it.value.uppercase()
                    )
                }
        }

        val at =
            cleaned.firstNotNullOfOrNull { texto ->
                atRegex
                    .find(
                        texto
                            .replace(" ", "")
                            .uppercase()
                    )
                    ?.value
                    ?.uppercase()
            }

        val currentTracking =
            codigos.singleOrNull()

        val nome =
            if (currentTracking != null) {
                findLabelValue(
                    cleaned,
                    listOf(
                        "nome",
                        "cliente",
                        "destinatário",
                        "destinatario",
                        "recebedor"
                    )
                )
            } else {
                null
            }

        val endereco =
            if (currentTracking != null) {
                findLabelValue(
                    cleaned,
                    listOf(
                        "endereço",
                        "endereco",
                        "end"
                    )
                )
            } else {
                null
            }

        val bairro =
            if (currentTracking != null) {
                findLabelValue(
                    cleaned,
                    listOf("bairro")
                )
            } else {
                null
            }

        val telefone =
            if (currentTracking != null) {
                phoneRegex
                    .find(allText)
                    ?.value
            } else {
                null
            }

        val status =
            if (currentTracking != null) {
                detectStatus(lower)
            } else {
                null
            }

        return SpxScreenSnapshot(
            at = at,
            expectedTotal =
                findExpectedTotal(cleaned),
            trackingCodes = codigos,
            currentTrackingCode =
                currentTracking,
            customerName = nome,
            phone = telefone,
            address = endereco,
            neighborhood = bairro,
            status = status,
            looksLikeLogin =
                looksLikeLogin(lower),
            looksAuthenticated =
                looksAuthenticated(lower)
        )
    }

    private fun findExpectedTotal(
        textos: List<String>
    ): Int? {

        var maior: Int? = null

        textos.forEach { texto ->

            fractionRegex
                .findAll(texto)
                .forEach { match ->

                    val atual =
                        match
                            .groupValues[1]
                            .toIntOrNull()

                    val total =
                        match
                            .groupValues[2]
                            .toIntOrNull()

                    if (
                        atual != null &&
                        total != null &&
                        atual >= 0 &&
                        atual <= total &&
                        total in 1..1000
                    ) {
                        if (
                            maior == null ||
                            total > maior!!
                        ) {
                            maior = total
                        }
                    }
                }

            totalTextRegex
                .findAll(texto)
                .forEach { match ->

                    val total =
                        match
                            .groupValues[1]
                            .toIntOrNull()

                    if (
                        total != null &&
                        total in 1..1000
                    ) {
                        if (
                            maior == null ||
                            total > maior!!
                        ) {
                            maior = total
                        }
                    }
                }
        }

        return maior
    }

    private fun findLabelValue(
        textos: List<String>,
        labels: List<String>
    ): String? {

        for (index in textos.indices) {

            val texto =
                textos[index]

            for (label in labels) {

                val regex =
                    Regex(
                        """^\s*${Regex.escape(label)}\s*:\s*(.+)$""",
                        RegexOption.IGNORE_CASE
                    )

                val match =
                    regex.find(texto)

                if (match != null) {
                    return match
                        .groupValues[1]
                        .trim()
                        .takeIf {
                            it.isNotBlank()
                        }
                }

                if (
                    texto.equals(
                        label,
                        true
                    ) ||
                    texto.equals(
                        "$label:",
                        true
                    )
                ) {
                    return textos
                        .getOrNull(index + 1)
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                }
            }
        }

        return null
    }

    private fun detectStatus(
        lower: String
    ): DeliveryStatus? {

        if (
            lower.contains(
                "entregue com sucesso"
            ) ||
            lower.contains(
                "pedido entregue"
            ) ||
            lower.contains(
                "entrega concluída"
            ) ||
            lower.contains(
                "entrega concluida"
            )
        ) {
            return DeliveryStatus.DELIVERED
        }

        if (
            lower.contains(
                "ocorrência registrada"
            ) ||
            lower.contains(
                "ocorrencia registrada"
            ) ||
            lower.contains(
                "com ocorrência"
            ) ||
            lower.contains(
                "com ocorrencia"
            )
        ) {
            return DeliveryStatus.OCCURRENCE
        }

        if (
            lower.contains(
                "retornar depois"
            ) ||
            lower.contains(
                "tentar novamente"
            )
        ) {
            return DeliveryStatus.RETURN_LATER
        }

        if (
            lower.contains(
                "em rota"
            ) ||
            lower.contains(
                "para entregar"
            )
        ) {
            return DeliveryStatus.OUT_FOR_DELIVERY
        }

        return null
    }

    private fun looksLikeLogin(
        lower: String
    ): Boolean {

        val fortes =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "código de verificação",
                "codigo de verificacao"
            )

        if (
            fortes.any {
                lower.contains(it)
            }
        ) {
            return true
        }

        val fracos =
            listOf(
                "login",
                "senha",
                "e-mail",
                "email",
                "telefone",
                "entrar"
            )

        return fracos.count {
            lower.contains(it)
        } >= 2
    }

    private fun looksAuthenticated(
        lower: String
    ): Boolean {

        return listOf(
            "entrega",
            "entregas",
            "rota",
            "rotas",
            "pacote",
            "pacotes",
            "em rota",
            "escanear",
            "ocorrência",
            "ocorrencia",
            "entregue"
        ).any {
            lower.contains(it)
        }
    }
}
