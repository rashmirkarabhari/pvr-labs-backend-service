package com.pvrlabs.payment.service;

import com.pvrlabs.payment.config.MailProperties;
import com.pvrlabs.payment.dto.request.CreateOrderRequestDto;
import com.pvrlabs.payment.dto.request.CustomerDetailsDto;
import com.pvrlabs.payment.dto.request.OrderItemSnapshotDto;
import com.pvrlabs.payment.dto.request.OrderSnapshotDto;
import com.pvrlabs.payment.dto.request.ShippingAddressDto;
import com.pvrlabs.payment.dto.response.PaymentStatusResponseDto;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEmailService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));
    private static final NumberFormat INR = createInrFormat();

    private final MailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSender;

    public boolean isValidEmail(String email) {
        return StringUtils.hasText(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public void sendAdminOrderEmail(String orderId,
                                    PaymentStatusResponseDto status,
                                    CheckoutOrderStore.Record record) {
        String adminTo = mailProperties.adminTo();
        if (!isValidEmail(adminTo)) {
            log.warn("Skipping admin order email | orderId={} reason=invalid or missing MAIL_ADMIN_TO", orderId);
            return;
        }
        String subject = "New Order Placed - Order #" + orderId;
        String html = buildHtml(orderId, status, record, true);
        send(adminTo, subject, html, orderId, "admin");
    }

    public void sendCustomerOrderEmail(String orderId,
                                       PaymentStatusResponseDto status,
                                       CheckoutOrderStore.Record record) {
        String customerEmail = resolveCustomerEmail(record);
        if (!isValidEmail(customerEmail)) {
            log.warn("Skipping customer order email | orderId={} reason=missing or invalid checkout email", orderId);
            return;
        }
        String subject = "Order Placed Successfully - Order #" + orderId;
        String html = buildHtml(orderId, status, record, false);
        send(customerEmail.trim(), subject, html, orderId, "customer");
    }

    public String resolveCustomerEmail(CheckoutOrderStore.Record record) {
        if (record == null || record.getRequest() == null) {
            return null;
        }
        CreateOrderRequestDto request = record.getRequest();
        OrderSnapshotDto snapshot = request.getOrderSnapshot();
        if (snapshot != null && snapshot.getShipping() != null
                && StringUtils.hasText(snapshot.getShipping().getEmail())) {
            return snapshot.getShipping().getEmail();
        }
        CustomerDetailsDto customer = request.getCustomerDetails();
        return customer != null ? customer.getCustomerEmail() : null;
    }

    private void send(String to, String subject, String html, String orderId, String kind) {
        if (!mailProperties.enabled() || !StringUtils.hasText(mailProperties.from())) {
            log.info(
                    "Mail disabled or from-address missing; logging {} email | orderId={} to={} subject={}",
                    kind, orderId, to, subject
            );
            log.debug("Email HTML ({}): {}", kind, html);
            return;
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("JavaMailSender is not configured; logging {} email | orderId={} to={}", kind, orderId, to);
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from(), mailProperties.fromName() != null ? mailProperties.fromName() : "PVR 3D Labs");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("Sent {} order email | orderId={} to={}", kind, orderId, to);
        } catch (Exception ex) {
            log.error("Failed to send {} order email | orderId={} to={}: {}", kind, orderId, to, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to send " + kind + " order email", ex);
        }
    }

    private String buildHtml(String orderId,
                             PaymentStatusResponseDto status,
                             CheckoutOrderStore.Record record,
                             boolean admin) {
        CreateOrderRequestDto request = record != null ? record.getRequest() : null;
        OrderSnapshotDto snapshot = request != null ? request.getOrderSnapshot() : null;
        CustomerDetailsDto customer = request != null ? request.getCustomerDetails() : null;
        ShippingAddressDto shipping = snapshot != null ? snapshot.getShipping() : null;

        String customerName = firstNonBlank(
                shipping != null ? shipping.getFullName() : null,
                customer != null ? customer.getCustomerName() : null,
                "Customer"
        );
        String customerEmail = resolveCustomerEmail(record);
        String customerPhone = firstNonBlank(
                shipping != null ? shipping.getPhone() : null,
                customer != null ? customer.getCustomerPhone() : null
        );

        Instant placedAt = record != null && record.getCreatedAt() != null
                ? record.getCreatedAt()
                : Instant.now();

        BigDecimal amountPaid = status.getAmountPaid() != null
                ? status.getAmountPaid()
                : status.getOrderAmount();
        String paymentRef = StringUtils.hasText(status.getCfPaymentId()) ? status.getCfPaymentId() : "—";
        String currency = StringUtils.hasText(status.getOrderCurrency()) ? status.getOrderCurrency() : "INR";

        StringBuilder itemsRows = new StringBuilder();
        if (snapshot != null && snapshot.getItems() != null && !snapshot.getItems().isEmpty()) {
            for (OrderItemSnapshotDto item : snapshot.getItems()) {
                BigDecimal price = nvl(item.getPrice());
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal line = price.multiply(BigDecimal.valueOf(qty));
                itemsRows.append("<tr>")
                        .append("<td style=\"padding:10px 12px;border-bottom:1px solid #eee;\">")
                        .append(escape(item.getName()));
                if (StringUtils.hasText(item.getVariant())) {
                    itemsRows.append("<div style=\"color:#667085;font-size:12px;\">")
                            .append(escape(item.getVariant())).append("</div>");
                }
                itemsRows.append("</td>")
                        .append("<td style=\"padding:10px 12px;border-bottom:1px solid #eee;text-align:center;\">")
                        .append(qty).append("</td>")
                        .append("<td style=\"padding:10px 12px;border-bottom:1px solid #eee;text-align:right;\">")
                        .append(formatInr(price)).append("</td>")
                        .append("<td style=\"padding:10px 12px;border-bottom:1px solid #eee;text-align:right;\">")
                        .append(formatInr(line)).append("</td>")
                        .append("</tr>");
            }
        } else {
            itemsRows.append("<tr><td colspan=\"4\" style=\"padding:10px 12px;\">Order items were not available on this notification.</td></tr>");
        }

        String intro = admin
                ? "A new paid order has been placed on PVR 3D Labs."
                : "Thank you for your order. Your order has been successfully placed and your payment has been received.";

        String heading = admin ? "New Order Placed" : "Order Confirmed";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#1d2939;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f4f6f8;padding:24px 12px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;\">")
                .append("<tr><td style=\"background:#111827;color:#fff;padding:24px 28px;\">")
                .append("<div style=\"font-size:13px;letter-spacing:0.08em;text-transform:uppercase;opacity:0.8;\">PVR 3D Labs</div>")
                .append("<h1 style=\"margin:8px 0 0;font-size:24px;\">").append(heading).append("</h1>")
                .append("</td></tr>")
                .append("<tr><td style=\"padding:28px;\">")
                .append("<p style=\"margin:0 0 20px;line-height:1.6;\">").append(escape(intro)).append("</p>")
                .append("<h2 style=\"font-size:16px;margin:0 0 12px;\">Order Information</h2>")
                .append(kv("Order Number", orderId))
                .append(kv("Order Date", DATE_FORMAT.format(placedAt) + " IST"))
                .append(kv("Payment Status", "Paid"))
                .append(kv("Payment Method", "Cashfree"))
                .append(kv("Payment Gateway", "Cashfree"))
                .append(kv("Payment Reference", paymentRef))
                .append(kv("Currency", currency));

        if (admin) {
            html.append("<h2 style=\"font-size:16px;margin:24px 0 12px;\">Customer</h2>")
                    .append(kv("Name", customerName))
                    .append(kv("Email", customerEmail != null ? customerEmail : "—"))
                    .append(kv("Phone", customerPhone != null ? customerPhone : "—"));
        } else {
            html.append(kv("Customer Name", customerName));
        }

        if (shipping != null) {
            html.append("<h2 style=\"font-size:16px;margin:24px 0 12px;\">Shipping Address</h2>")
                    .append("<p style=\"margin:0;line-height:1.6;\">")
                    .append(formatAddress(shipping))
                    .append("</p>");
        }

        html.append("<h2 style=\"font-size:16px;margin:24px 0 12px;\">Items</h2>")
                .append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;font-size:14px;\">")
                .append("<tr style=\"background:#f8fafc;text-align:left;\">")
                .append("<th style=\"padding:10px 12px;\">Product</th>")
                .append("<th style=\"padding:10px 12px;text-align:center;\">Qty</th>")
                .append("<th style=\"padding:10px 12px;text-align:right;\">Price</th>")
                .append("<th style=\"padding:10px 12px;text-align:right;\">Line Total</th>")
                .append("</tr>")
                .append(itemsRows)
                .append("</table>")
                .append("<h2 style=\"font-size:16px;margin:24px 0 12px;\">Order Summary</h2>");

        if (snapshot != null) {
            html.append(kv("Subtotal", formatInr(snapshot.getSubtotal())));
            if (nvl(snapshot.getGiftWrap()).signum() > 0) {
                html.append(kv("Gift Wrap", formatInr(snapshot.getGiftWrap())));
            }
            if (nvl(snapshot.getDiscount()).signum() > 0) {
                html.append(kv("Discount", "-" + formatInr(snapshot.getDiscount())));
            }
            html.append(kv("Shipping", nvl(snapshot.getDelivery()).signum() == 0 ? "FREE" : formatInr(snapshot.getDelivery())))
                    .append(kv("Tax", formatInr(snapshot.getTax())))
                    .append(kv("Total", formatInr(snapshot.getTotal() != null ? snapshot.getTotal() : status.getOrderAmount())));
        } else {
            html.append(kv("Total", formatInr(status.getOrderAmount())));
        }

        html.append(kv("Amount Paid", formatInr(amountPaid)))
                .append("<p style=\"margin:28px 0 0;color:#667085;font-size:12px;line-height:1.5;\">")
                .append("This confirmation was sent after Cashfree verified a successful payment. ")
                .append("If you have questions, reply to this email or contact hello@pvr3dlabs.com.")
                .append("</p>")
                .append("</td></tr></table></td></tr></table></body></html>");

        return html.toString();
    }

    private String formatAddress(ShippingAddressDto shipping) {
        List<String> parts = new java.util.ArrayList<>();
        addIfText(parts, shipping.getFullName());
        addIfText(parts, shipping.getAddress1());
        addIfText(parts, shipping.getAddress2());
        String cityLine = joinNonBlank(", ", shipping.getCity(), shipping.getState(), shipping.getZip());
        addIfText(parts, cityLine);
        addIfText(parts, shipping.getCountry());
        return String.join("<br>", parts.stream().map(this::escape).toList());
    }

    private void addIfText(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value);
        }
    }

    private String kv(String label, String value) {
        return "<div style=\"display:flex;justify-content:space-between;gap:16px;padding:6px 0;border-bottom:1px solid #f2f4f7;font-size:14px;\">"
                + "<span style=\"color:#667085;\">" + escape(label) + "</span>"
                + "<span style=\"font-weight:600;\">" + escape(value != null ? value : "—") + "</span></div>";
    }

    private String formatInr(BigDecimal amount) {
        return INR.format(nvl(amount));
    }

    private static NumberFormat createInrFormat() {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setCurrency(Currency.getInstance("INR"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String joinNonBlank(String delimiter, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
