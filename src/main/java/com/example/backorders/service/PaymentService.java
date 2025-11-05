package com.example.backorders.service;

import com.example.backorders.model.Order;
import com.example.backorders.exceptions.*;

/**
 * Servicio que simula la interacción con una pasarela de pagos.
 * Para pruebas y demostración usa reglas simples para forzar distintos errores.
 */
public class PaymentService {

    /**
     * Procesa el pago de la orden. Lanza una excepción específica si ocurre un fallo.
     */
    public void processPayment(Order order) {
        if (order == null) {
            throw new PaymentApiException("Orden nula");
        }

        Double amount = order.getTotalAmount();
        if (amount == null || amount < 0) {
            throw new PaymentApiException("Monto de la orden inválido");
        }

        long id = order.getId() != null ? order.getId() : 0L;

        // Reglas de simulación (determinísticas, fáciles de probar):
        // - Si el id es múltiplo de 7 -> error de conexión API
        // - Si el total > 10000 -> saldo insuficiente
        // - Si el id es múltiplo de 5 -> pago duplicado
        if (id % 7 == 0 && id != 0) {
            throw new PaymentApiException("Error de conexión con la pasarela de pagos");
        }

        if (amount > 10000) {
            throw new InsufficientFundsException("Saldo insuficiente");
        }

        if (id % 5 == 0 && id != 0) {
            throw new DuplicatePaymentException("Pago duplicado detectado");
        }

        // Si llegamos aquí, simulamos pago exitoso (no hace nada más)
        // En un caso real, aquí se llamaría la API, se guardaría la transacción, etc.
    }
}
