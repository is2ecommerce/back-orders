import com.example.backorders.model.Order;
import com.example.backorders.model.OrderItem;
import com.example.backorders.model.Product;
import com.example.backorders.Repositories.OrderRepository;
import com.example.backorders.Repositories.ProductRepository;
import com.example.backorders.exceptions.OrderStateException;
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

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> confirmDelivery(Long id, String userId) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return Optional.empty();

        Order order = orderOpt.get();

        if (order.getUserId() == null || !order.getUserId().equals(userId)) {
            return Optional.empty();
        }

        String currentState = order.getStatus();
        if (!"en camino".equals(currentState) && !"pendiente de entrega".equals(currentState)) {
            throw new OrderStateException(currentState, "en camino/pendiente de entrega");
        }

        order.setStatus("entregada");
        orderRepository.save(order);
        sendDeliveryConfirmationNotification(order);
        return Optional.of(order);
    }

    private void sendDeliveryConfirmationNotification(Order order) {
        System.out.println("Notificación enviada al usuario " + order.getUserId() +
                ": Su orden #" + order.getId() + " ha sido confirmada como entregada.");
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
            orderRepository.save(order);
            return Optional.of(order);
        }

        return Optional.empty();
    }

    public Optional<Order> payOrder(Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return Optional.empty();

        Order order = orderOpt.get();
        if (!"pendiente".equals(order.getStatus())) return Optional.empty();

        order.setStatus("pagada");
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




