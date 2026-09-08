package com.routecopilot.data.repository

import com.routecopilot.data.model.Delivery
import com.routecopilot.data.model.DeliveryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RouteRepository {

    private val _deliveries =
        MutableStateFlow<Map<String, Delivery>>(emptyMap())

    val deliveries: StateFlow<Map<String, Delivery>> =
        _deliveries.asStateFlow()

    fun clear() {
        _deliveries.value = emptyMap()
    }

    fun ensureDelivery(
        trackingCode: String,
        spxOrder: Int? = null
    ) {
        val code = trackingCode.trim().uppercase()
        if (code.isBlank()) return
        if (_deliveries.value.containsKey(code)) return

        val novaLista = _deliveries.value.toMutableMap()
        novaLista[code] = Delivery(
            trackingCode = code,
            spxOrder = spxOrder
        )
        _deliveries.value = novaLista
    }

    fun upsertDelivery(
        trackingCode: String,
        customerName: String? = null,
        phone: String? = null,
        address: String? = null,
        neighborhood: String? = null,
        status: DeliveryStatus? = null,
        spxOrder: Int? = null
    ) {
        val code = trackingCode.trim().uppercase()
        if (code.isBlank()) return

        val atual =
            _deliveries.value[code]
                ?: Delivery(trackingCode = code)

        val atualizado = atual.copy(
            customerName =
                customerName?.trim()?.takeIf { it.isNotBlank() }
                    ?: atual.customerName,

            phone =
                phone?.trim()?.takeIf { it.isNotBlank() }
                    ?: atual.phone,

            address =
                address?.trim()?.takeIf { it.isNotBlank() }
                    ?: atual.address,

            neighborhood =
                neighborhood?.trim()?.takeIf { it.isNotBlank() }
                    ?: atual.neighborhood,

            status = status ?: atual.status,

            spxOrder = spxOrder ?: atual.spxOrder,

            lastUpdatedAt = System.currentTimeMillis()
        )

        val novaLista = _deliveries.value.toMutableMap()
        novaLista[code] = atualizado
        _deliveries.value = novaLista
    }

    fun updateStatus(
        trackingCode: String,
        status: DeliveryStatus
    ) {
        upsertDelivery(
            trackingCode = trackingCode,
            status = status
        )
    }

    fun getDelivery(
        trackingCode: String
    ): Delivery? {
        return _deliveries.value[
            trackingCode.trim().uppercase()
        ]
    }

    fun total(): Int =
        _deliveries.value.size

    fun pendingCount(): Int =
        _deliveries.value.values.count {
            it.status == DeliveryStatus.PENDING ||
                it.status == DeliveryStatus.OUT_FOR_DELIVERY ||
                it.status == DeliveryStatus.UNKNOWN
        }

    fun deliveredCount(): Int =
        _deliveries.value.values.count {
            it.status == DeliveryStatus.DELIVERED
        }

    fun occurrenceCount(): Int =
        _deliveries.value.values.count {
            it.status == DeliveryStatus.OCCURRENCE
        }
}
