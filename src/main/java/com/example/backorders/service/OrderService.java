package com.example.backorders.service;

import com.example.backorders.exceptions.DuplicatePaymentException;
import com.example.backorders.exceptions.InsufficientFundsException;
import com.example.backorders.exceptions.OrderStateException;
import com.example.backorders.exceptions.PaymentApiException;
import com.example.backorders.model.Order;
import com.example.backorders.model.OrderItem;
import com.example.backorders.model.Product;
import com.example.backorders.Repositories.OrderRepository;
import com.example.backorders.Repositories.ProductRepository;
import com.example.backorders.dto.OrderSummaryDTO;
import com.example.backorders.dto.OrderItemDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.ByteArrayOutputStream;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentService = new PaymentService();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> confirmDelivery(Long id, String userId) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }

        Order order = orderOpt.get();

        if (order.getUserId() == null || !order.getUserId().equals(userId)) {
            return Optional.empty();
        }

        String currentState = order.getStatus();
        if (!Order.STATUS_IN_DELIVERY.equals(currentState) && !Order.STATUS_PENDING_DELIVERY.equals(currentState)) {
            throw new OrderStateException(currentState, "en camino/pendiente de entrega");
        }

        order.setStatus(Order.STATUS_DELIVERED);
        orderRepository.save(order);
        sendDeliveryConfirmationNotification(order);

        return Optional.of(order);
    }

    /**
     * Procesa el pago de una orden. Valida estado y delega a PaymentService.
     * Lanza excepciones específicas en caso de error para que el GlobalExceptionHandler
     * las transforme en respuestas HTTP adecuadas.
     */
    public Order processPayment(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (!orderOpt.isPresent()) {
            throw new RuntimeException("Orden no encontrada");
        }

        Order order = orderOpt.get();

        // Solo permitir pago si la orden está en estado 'pendiente'
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new OrderStateException(order.getStatus(), Order.STATUS_PENDING);
        }

        // No permitir si ya está cancelada
        validateOrderNotCancelled(order);

        try {
            paymentService.processPayment(order);
        } catch (PaymentApiException | InsufficientFundsException | DuplicatePaymentException e) {
            // Re-lanzar para que el GlobalExceptionHandler procese la respuesta adecuada
            throw e;
        } catch (RuntimeException e) {
            // Otros errores de pago: envolver en PaymentApiException
            throw new PaymentApiException("Error procesando el pago: " + e.getMessage(), e);
        }

        // Si paymentService no lanzó excepciones, marcamos la orden como pagada
        order.setStatus(Order.STATUS_PAID);
        Order saved = orderRepository.save(order);

        // Simular envío de confirmación al usuario
        sendPaymentConfirmationNotification(saved);

        return saved;
    }

    private void validateOrderNotCancelled(Order order) {
        if (Order.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderStateException(order.getStatus(), "activo");
        }
    }

    private void validateNewStatus(String newStatus) {
        if (!isValidStatus(newStatus)) {
            throw new IllegalArgumentException("Estado inválido: " + newStatus);
        }
    }

    private boolean isValidStatus(String status) {
        return status.equals(Order.STATUS_PENDING) ||
               status.equals(Order.STATUS_COMPLETED) ||
               status.equals(Order.STATUS_CANCELLED) ||
               status.equals(Order.STATUS_IN_DELIVERY) ||
               status.equals(Order.STATUS_PENDING_DELIVERY) ||
               status.equals(Order.STATUS_DELIVERED) ||
               status.equals(Order.STATUS_PAID);
    }

    private void sendDeliveryConfirmationNotification(Order order) {
        // Simulación de envío de notificación
        System.out.println("Notificación enviada al usuario " + order.getUserId() +
                          ": Su orden #" + order.getId() + " ha sido confirmada como entregada.");
    }

    private void sendPaymentConfirmationNotification(Order order) {
        System.out.println("Notificación de pago enviada al usuario " + order.getUserId() +
                          ": Su pago para la orden #" + order.getId() + " fue procesado correctamente.");
    }

    public List<OrderSummaryDTO> getOrdersByUserId(String userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<OrderSummaryDTO> result = new ArrayList<>();

        for (Order o : orders) {
            List<OrderItemDTO> items = new ArrayList<>();
            if (o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    Long pid = it.getProduct() != null ? it.getProduct().getId() : null;
                    items.add(new OrderItemDTO(pid, it.getQuantity(), it.getPrice()));
                }
            }
            result.add(new OrderSummaryDTO(o.getId(), o.getCreatedAt(), o.getStatus(), o.getTotalAmount(), items));
        }

        return result;
    }

    public Optional<Order> cancelOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();

            if (!Order.STATUS_PENDING.equals(order.getStatus())) {
                return Optional.empty();
            }

            // Reponer stock
            List<OrderItem> items = order.getItems();
            if (items != null) {
                for (OrderItem item : items) {
                    Product product = item.getProduct();
                    if (product != null) {
                        product.setStock(product.getStock() + item.getQuantity());
                        productRepository.save(product);
                    }
                }
            }

            // Marcar orden cancelada
            order.setStatus(Order.STATUS_CANCELLED);
            orderRepository.save(order);
            return Optional.of(order);
        }

        return Optional.empty();
    }

    // Método simple para compatibilidad: marcar como pagada (usa el mismo flujo de processPayment preferiblemente)
    public Optional<Order> payOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        Order order = orderOpt.get();
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            return Optional.empty();
        }
        order.setStatus(Order.STATUS_PAID);
        orderRepository.save(order);
        return Optional.of(order);
    }

    // ==============================================================
    // FILTROS POR ESTADO + FECHA + PAGINACIÓN
    // ==============================================================
    public Page<OrderSummaryDTO> getOrdersByUserId(
            String userId,
            String status,
            String fechaInicio,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        Date fecha = null;
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            try {
                LocalDate localDate = LocalDate.parse(fechaInicio);
                fecha = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        // Simplificamos la lógica usando el método flexible del repositorio
        return orderRepository.findByUserIdAndOptionalFilters(userId, status, fecha, pageable)
                .map(this::toSummaryDTO);
    }

    private OrderSummaryDTO toSummaryDTO(Order order) {
        List<OrderItemDTO> items = order.getItems() != null
                ? order.getItems().stream()
                        .map(it -> new OrderItemDTO(
                                it.getProduct() != null ? it.getProduct().getId() : null,
                                it.getQuantity(),
                                it.getPrice()))
                        .toList()
                : List.of();

        return new OrderSummaryDTO(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getTotalAmount(),
                items
        );
    }

    // ==============================================================
    // HU-5: GENERAR RECIBO DE PAGO EN PDF
    // ==============================================================
    public byte[] generateReceiptPdf(Order order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // === ENCABEZADO ===
            document.add(new Paragraph("RECIBO DE PAGO")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("IS2 E-COMMERCE")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            // === DATOS PRINCIPALES ===
            Table table = new Table(2).useAllAvailableWidth();
            table.addCell(createCell("Orden ID:"));
            table.addCell(createCell(order.getId().toString()));

            table.addCell(createCell("Fecha:"));
            table.addCell(createCell(order.getCreatedAt().toString()));

            table.addCell(createCell("Estado:"));
            table.addCell(createCell(order.getStatus()));

            table.addCell(createCell("Total:"));
            table.addCell(createCell("$" + order.getTotalAmount()));

            document.add(table);
            document.add(new Paragraph("\n"));

            // === DETALLE DE PRODUCTOS ===
            document.add(new Paragraph("Detalles:")
                    .setBold()
                    .setFontSize(14));

            Table itemsTable = new Table(new float[]{3, 1, 2}).useAllAvailableWidth();
            itemsTable.addHeaderCell("Producto");
            itemsTable.addHeaderCell("Cant.");
            itemsTable.addHeaderCell("Precio");

            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    Product p = item.getProduct();
                    itemsTable.addCell(p != null ? p.getName() : "N/A");
                    itemsTable.addCell(String.valueOf(item.getQuantity()));
                    itemsTable.addCell("$" + item.getPrice());
                }
            }

            document.add(itemsTable);
            document.add(new Paragraph("\n¡Gracias por su compra!")
                    .setTextAlignment(TextAlignment.CENTER));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    private Cell createCell(String text) {
        return new Cell().add(new Paragraph(text));
    }
}
import com.example.backorders.model.OrderItem;
import com.example.backorders.model.Product;
import com.example.backorders.Repositories.OrderRepository;
import com.example.backorders.Repositories.ProductRepository;
import com.example.backorders.exceptions.OrderStateException;
import com.example.backorders.dto.OrderSummaryDTO;
import com.example.backorders.dto.OrderItemDTO;

<<<<<<< HEAD
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import java.io.ByteArrayOutputStream;


@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
=======
import java.util.List;
import java.util.Optional;

public class OrderService {

    private final OrderRepositorio orderRepository;
    private final ProductRepositorio productRepository;
    private final PaymentService paymentService;
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentService = new PaymentService();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> confirmDelivery(Long id, String userId) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return Optional.empty();

        Order order = orderOpt.get();

<<<<<<< HEAD
        if (order.getUserId() == null || !order.getUserId().equals(userId)) {
=======
        // Validar que la orden pertenece al usuario
        if (!userId.equals(order.getUserId())) {
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)
            return Optional.empty();
        }

        String currentState = order.getStatus();
        if (!Order.STATUS_IN_DELIVERY.equals(currentState) && !Order.STATUS_PENDING_DELIVERY.equals(currentState)) {
            throw new OrderStateException(currentState, "en camino/pendiente de entrega");
        }

<<<<<<< HEAD
        order.setStatus("entregada");
=======
        // Actualizar estado
        order.setStatus(Order.STATUS_DELIVERED);
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)
        orderRepository.save(order);
        sendDeliveryConfirmationNotification(order);
<<<<<<< HEAD
        return Optional.of(order);
    }

    private void sendDeliveryConfirmationNotification(Order order) {
        System.out.println("Notificación enviada al usuario " + order.getUserId() +
                ": Su orden #" + order.getId() + " ha sido confirmada como entregada.");
    }

    public List<OrderSummaryDTO> getOrdersByUserId(String userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<OrderSummaryDTO> result = new ArrayList<>();
=======

        return Optional.of(order);
    }

    public Order updateOrderStatus(Long orderId, String newStatus) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (!orderOpt.isPresent()) {
            throw new RuntimeException("Orden no encontrada");
        }

        Order order = orderOpt.get();
        validateOrderNotCancelled(order);

        // Validar que el nuevo estado sea válido
        validateNewStatus(newStatus);

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    /**
     * Procesa el pago de una orden. Valida estado y delega a PaymentService.
     * Lanza excepciones específicas en caso de error para que el GlobalExceptionHandler
     * las transforme en respuestas HTTP adecuadas.
     */
    public Order processPayment(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (!orderOpt.isPresent()) {
            throw new RuntimeException("Orden no encontrada");
        }

        Order order = orderOpt.get();

        // Solo permitir pago si la orden está en estado 'pendiente'
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            throw new OrderStateException(order.getStatus(), Order.STATUS_PENDING);
        }

        // No permitir si ya está cancelada
        validateOrderNotCancelled(order);

        try {
            paymentService.processPayment(order);
        } catch (PaymentApiException | InsufficientFundsException | DuplicatePaymentException e) {
            // Re-lanzar para que el GlobalExceptionHandler procese la respuesta adecuada
            throw e;
        } catch (RuntimeException e) {
            // Otros errores de pago: envolver en PaymentApiException
            throw new PaymentApiException("Error procesando el pago: " + e.getMessage(), e);
        }

        // Si paymentService no lanzó excepciones, marcamos la orden como pagada
        order.setStatus(Order.STATUS_PAID);
        Order saved = orderRepository.save(order);

        // Simular envío de confirmación al usuario
        sendPaymentConfirmationNotification(saved);

        return saved;
    }

    private void validateOrderNotCancelled(Order order) {
        if (Order.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderStateException(order.getStatus(), "activo");
        }
    }

    private void validateNewStatus(String newStatus) {
        if (!isValidStatus(newStatus)) {
            throw new IllegalArgumentException("Estado inválido: " + newStatus);
        }
    }

    private boolean isValidStatus(String status) {
        return status.equals(Order.STATUS_PENDING) ||
               status.equals(Order.STATUS_COMPLETED) ||
               status.equals(Order.STATUS_CANCELLED) ||
               status.equals(Order.STATUS_IN_DELIVERY) ||
               status.equals(Order.STATUS_PENDING_DELIVERY) ||
               status.equals(Order.STATUS_DELIVERED) ||
               status.equals(Order.STATUS_PAID);
    }

    private void sendDeliveryConfirmationNotification(Order order) {
        // Simulación de envío de notificación
        System.out.println("Notificación enviada al usuario " + order.getUserId() +
                          ": Su orden #" + order.getId() + " ha sido confirmada como entregada.");
    }

    private void sendPaymentConfirmationNotification(Order order) {
        System.out.println("Notificación de pago enviada al usuario " + order.getUserId() +
                          ": Su pago para la orden #" + order.getId() + " fue procesado correctamente.");
    }

    public java.util.List<com.example.backorders.dto.OrderSummaryDTO> getOrdersByUserId(String userId) {
        java.util.List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        java.util.List<com.example.backorders.dto.OrderSummaryDTO> result = new java.util.ArrayList<>();
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)

        for (Order o : orders) {
            List<OrderItemDTO> items = new ArrayList<>();
            if (o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    Long pid = it.getProduct() != null ? it.getProduct().getId() : null;
                    items.add(new OrderItemDTO(pid, it.getQuantity(), it.getPrice()));
                }
            }
            result.add(new OrderSummaryDTO(o.getId(), o.getCreatedAt(), o.getStatus(), o.getTotalAmount(), items));
        }

        return result;
    }

    public Optional<Order> cancelOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();

<<<<<<< HEAD
            if (!"pendiente".equals(order.getStatus())) {
                return Optional.empty();
            }

            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    if (item == null) continue;
                    Product product = item.getProduct();
                    if (product == null) continue;
                    product.setStock(product.getStock() + item.getQuantity());
                    productRepository.save(product);
                }
            }

            order.setStatus("cancelada");
=======
            if (!Order.STATUS_PENDING.equals(order.getStatus())) {
                return Optional.empty();
            }

            // Reponer stock
            List<OrderItem> items = order.getItems();
            if (items != null) {
                for (OrderItem item : items) {
                    Product product = item.getProduct();
                    if (product != null) {
                        product.setStock(product.getStock() + item.getQuantity());
                        productRepository.save(product);
                    }
                }
            }

            // Marcar orden cancelada
            order.setStatus(Order.STATUS_CANCELLED);
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)
            orderRepository.save(order);
            return Optional.of(order);
        }

        return Optional.empty();
    }

<<<<<<< HEAD
=======
    // Método simple para compatibilidad: marcar como pagada (usa el mismo flujo de processPayment preferiblemente)
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)
    public Optional<Order> payOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return Optional.empty();

        Order order = orderOpt.get();
<<<<<<< HEAD
        if (!"pendiente".equals(order.getStatus())) return Optional.empty();

        order.setStatus("pagada");
=======
        if (!Order.STATUS_PENDING.equals(order.getStatus())) {
            return Optional.empty();
        }
        order.setStatus(Order.STATUS_PAID);
>>>>>>> b2e4e1e (feat(payments): implementar HU-6 procesamiento de pagos - Luna Gaona)
        orderRepository.save(order);
        return Optional.of(order);
    }

    // ==============================================================
    // FILTROS POR ESTADO + FECHA + PAGINACIÓN
    // ==============================================================
    public Page<OrderSummaryDTO> getOrdersByUserId(
            String userId,
            String status,
            String fechaInicio,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        Date fecha = null;
        if (fechaInicio != null && !fechaInicio.isBlank()) {
            try {
                LocalDate localDate = LocalDate.parse(fechaInicio);
                fecha = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }

        // Simplificamos la lógica usando el método flexible del repositorio
        return orderRepository.findByUserIdAndOptionalFilters(userId, status, fecha, pageable)
                .map(this::toSummaryDTO);
    }

    private OrderSummaryDTO toSummaryDTO(Order order) {
        List<OrderItemDTO> items = order.getItems() != null
                ? order.getItems().stream()
                        .map(it -> new OrderItemDTO(
                                it.getProduct() != null ? it.getProduct().getId() : null,
                                it.getQuantity(),
                                it.getPrice()))
                        .toList()
                : List.of();

        return new OrderSummaryDTO(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus(),
                order.getTotalAmount(),
                items
        );
    }

    // ==============================================================
    // HU-5: GENERAR RECIBO DE PAGO EN PDF
    // ==============================================================
    public byte[] generateReceiptPdf(Order order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // === ENCABEZADO ===
            document.add(new Paragraph("RECIBO DE PAGO")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("IS2 E-COMMERCE")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            // === DATOS PRINCIPALES ===
            Table table = new Table(2).useAllAvailableWidth();
            table.addCell(createCell("Orden ID:"));
            table.addCell(createCell(order.getId().toString()));

            table.addCell(createCell("Fecha:"));
            table.addCell(createCell(order.getCreatedAt().toString()));

            table.addCell(createCell("Estado:"));
            table.addCell(createCell(order.getStatus()));

            table.addCell(createCell("Total:"));
            table.addCell(createCell("$" + order.getTotalAmount()));

            document.add(table);
            document.add(new Paragraph("\n"));

            // === DETALLE DE PRODUCTOS ===
            document.add(new Paragraph("Detalles:")
                    .setBold()
                    .setFontSize(14));

            Table itemsTable = new Table(new float[]{3, 1, 2}).useAllAvailableWidth();
            itemsTable.addHeaderCell("Producto");
            itemsTable.addHeaderCell("Cant.");
            itemsTable.addHeaderCell("Precio");

            for (OrderItem item : order.getItems()) {
                Product p = item.getProduct();
                itemsTable.addCell(p != null ? p.getName() : "N/A");
                itemsTable.addCell(String.valueOf(item.getQuantity()));
                itemsTable.addCell("$" + item.getPrice());
            }

            document.add(itemsTable);
            document.add(new Paragraph("\n¡Gracias por su compra!")
                .setTextAlignment(TextAlignment.CENTER));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    private Cell createCell(String content) {
        return new Cell().add(new Paragraph(content)).setPadding(5);
    }
}




