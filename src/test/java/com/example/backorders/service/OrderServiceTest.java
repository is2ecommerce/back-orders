package com.example.backorders.service;

import com.example.backorders.Repositories.OrderRepositorio;
import com.example.backorders.Repositories.ProductRepositorio;
import com.example.backorders.exceptions.*;
import com.example.backorders.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepositorio orderRepository;

    @Mock
    private ProductRepositorio productRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository);
    }

    @Nested
    @DisplayName("Tests de procesamiento de pagos")
    class PaymentProcessingTests {

        @Test
        @DisplayName("Debe procesar el pago exitosamente cuando la orden está pendiente")
        void shouldProcessPaymentSuccessfully() {
            // Arrange
            Order order = new Order();
            order.setId(1L);
            order.setStatus(Order.STATUS_PENDING);
            order.setTotalAmount(100.0);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Order result = orderService.processPayment(1L);

            // Assert
            assertNotNull(result);
            assertEquals(Order.STATUS_PAID, result.getStatus());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Debe lanzar OrderStateException cuando la orden no está pendiente")
        void shouldThrowOrderStateExceptionWhenNotPending() {
            // Arrange
            Order order = new Order();
            order.setId(1L);
            order.setStatus(Order.STATUS_CANCELLED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // Act & Assert
            OrderStateException exception = assertThrows(OrderStateException.class,
                () -> orderService.processPayment(1L));

            assertEquals(Order.STATUS_CANCELLED, exception.getCurrentState());
            assertEquals(Order.STATUS_PENDING, exception.getRequiredState());
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Debe lanzar InsufficientFundsException cuando el monto es mayor a 10000")
        void shouldThrowInsufficientFundsException() {
            // Arrange
            Order order = new Order();
            order.setId(1L);
            order.setStatus(Order.STATUS_PENDING);
            order.setTotalAmount(15000.0);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            // Act & Assert
            assertThrows(InsufficientFundsException.class,
                () -> orderService.processPayment(1L));

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Debe lanzar DuplicatePaymentException cuando el ID es múltiplo de 5")
        void shouldThrowDuplicatePaymentException() {
            // Arrange
            Order order = new Order();
            order.setId(5L);
            order.setStatus(Order.STATUS_PENDING);
            order.setTotalAmount(100.0);

            when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

            // Act & Assert
            assertThrows(DuplicatePaymentException.class,
                () -> orderService.processPayment(5L));

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Debe lanzar PaymentApiException cuando el ID es múltiplo de 7")
        void shouldThrowPaymentApiException() {
            // Arrange
            Order order = new Order();
            order.setId(7L);
            order.setStatus(Order.STATUS_PENDING);
            order.setTotalAmount(100.0);

            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            // Act & Assert
            assertThrows(PaymentApiException.class,
                () -> orderService.processPayment(7L));

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Debe lanzar RuntimeException cuando la orden no existe")
        void shouldThrowRuntimeExceptionWhenOrderNotFound() {
            // Arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class,
                () -> orderService.processPayment(1L));

            verify(orderRepository, never()).save(any(Order.class));
        }
    }
}