package com.routecopilot.route

data class CustomerQueueState(
    val next: RoutePackage?,
    val preAlert: RoutePackage?
)

object CustomerQueuePlanner {

    fun compute(route: ActiveRoute?): CustomerQueueState {
        if (route == null) return CustomerQueueState(null, null)

        val pending = route.packages
            .filter {
                it.status != DeliveryStatus.DELIVERED &&
                it.status != DeliveryStatus.OCCURRENCE
            }
            .sortedBy { it.sequence }

        return CustomerQueueState(
            next = pending.getOrNull(0),
            preAlert = pending.getOrNull(1)
        )
    }

    fun preAlertMessage(pkg: RoutePackage): String =
        "📦 Sua entrega está se aproximando. Você é uma das próximas paradas do entregador. " +
            "Fique atento ao telefone e, se possível, prepare-se para receber o pedido."

    fun nextMessage(pkg: RoutePackage): String =
        "🛵 Seu pedido é o próximo. O entregador está a caminho. " +
            "Fique atento para receber sua entrega."
}
