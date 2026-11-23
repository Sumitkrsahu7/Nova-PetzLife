package com.novapetzlife.support;

import com.novapetzlife.common.ResourceNotFoundException;
import com.novapetzlife.customer.Customer;
import com.novapetzlife.customer.CustomerRepository;
import com.novapetzlife.customer.CustomerService;
import com.novapetzlife.notification.NotificationService;
import com.novapetzlife.order.Order;
import com.novapetzlife.order.OrderRepository;
import com.novapetzlife.support.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final com.novapetzlife.delivery.ShopLocationRepository shopLocationRepository;
    private final com.novapetzlife.settings.WebsiteSettingService websiteSettingService;

    public SupportService(
            SupportTicketRepository ticketRepository,
            SupportMessageRepository messageRepository,
            OrderRepository orderRepository,
            CustomerRepository customerRepository,
            CustomerService customerService,
            NotificationService notificationService,
            com.novapetzlife.delivery.ShopLocationRepository shopLocationRepository,
            com.novapetzlife.settings.WebsiteSettingService websiteSettingService) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.notificationService = notificationService;
        this.shopLocationRepository = shopLocationRepository;
        this.websiteSettingService = websiteSettingService;
    }

    public StoreInfoDto getStoreInfo() {
        var shopOpt = shopLocationRepository.findByIsActiveTrue();
        var contactInfo = websiteSettingService.getContactInfo();

        String storeName = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getShopName).filter(org.springframework.util.StringUtils::hasText).orElse("NOVA PETZLIFE");
        String address = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getAddressLine).filter(org.springframework.util.StringUtils::hasText).orElse(contactInfo.address());
        String locality = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCity).filter(org.springframework.util.StringUtils::hasText).orElse("");
        String city = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCity).filter(org.springframework.util.StringUtils::hasText).orElse("");
        String state = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getState).filter(org.springframework.util.StringUtils::hasText).orElse("");
        String country = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCountry).filter(org.springframework.util.StringUtils::hasText).orElse("India");
        String postalCode = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getPinCode).filter(org.springframework.util.StringUtils::hasText).orElse("");
        Double lat = shopOpt.map(s -> s.getLatitude() != null ? s.getLatitude().doubleValue() : null).orElse(null);
        Double lng = shopOpt.map(s -> s.getLongitude() != null ? s.getLongitude().doubleValue() : null).orElse(null);
        Double radius = shopOpt.map(s -> s.getDeliveryRadiusKm() != null ? s.getDeliveryRadiusKm().doubleValue() : 20.0).orElse(20.0);

        return new StoreInfoDto(
                storeName,
                storeName,
                address,
                locality,
                city,
                state,
                country,
                postalCode,
                lat,
                lng,
                radius,
                contactInfo.phone(),
                contactInfo.whatsappNumber(),
                contactInfo.email(),
                contactInfo.timings(),
                List.of(
                        "Local Delivery within " + radius.intValue() + " km radius",
                        "Premium Dog & Cat Nutrition",
                        "Aquarium & Bird Supplies",
                        "Grooming & Hygiene Care Products",
                        "Interactive Toys & Durable Accessories",
                        "Same-Day Scheduled Delivery Windows"
                )
        );
    }

    public WhatsAppConfigDto getWhatsAppConfig(String product, String customMessage) {
        var contactInfo = websiteSettingService.getContactInfo();
        String rawNumber = contactInfo.whatsappNumber() != null && !contactInfo.whatsappNumber().isBlank()
                ? contactInfo.whatsappNumber()
                : "+91 94471 00000";

        // Extract digits only for wa.me link
        String digitsOnly = rawNumber.replaceAll("[^0-9]", "");
        if (!digitsOnly.startsWith("91") && digitsOnly.length() == 10) {
            digitsOnly = "91" + digitsOnly;
        }

        String msg;
        if (customMessage != null && !customMessage.isBlank()) {
            msg = customMessage.trim();
        } else if (product != null && !product.isBlank()) {
            msg = "Hi, I would like to know about " + product.trim();
        } else {
            msg = "Hi, I would like to know about this product";
        }

        String encodedMsg = java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://wa.me/" + digitsOnly + "?text=" + encodedMsg;

        return new WhatsAppConfigDto(digitsOnly, rawNumber, msg, url);
    }

    public List<FaqItemDto> getFaqs() {
        return List.of(
                new FaqItemDto(
                        "Delivery & Radius",
                        "What is the delivery coverage of NOVA PETZLIFE in Trivandrum?",
                        "We deliver within a strict 20 km straight-line radius from our store, covering Kowdiar, Pattom, Ambalamukku, Sasthamangalam, Palayam, Kazhakkoottam, Technopark, and nearby Thiruvananthapuram areas."
                ),
                new FaqItemDto(
                        "Delivery & Radius",
                        "What are the delivery slots and charges?",
                        "Orders above ₹500 qualify for FREE delivery. For smaller orders, a standard fee of ₹50 applies. We offer Morning (9AM-1PM), Afternoon (1PM-5PM), and Evening (5PM-9PM) delivery slots."
                ),
                new FaqItemDto(
                        "Returns & Refunds",
                        "What is your return and refund policy?",
                        "Unopened and undamaged accessories, toys, and grooming gear can be returned within 7 days of delivery. Consumable pet food, opened treats, and hygiene items are non-returnable to maintain strict pet safety."
                ),
                new FaqItemDto(
                        "Payments",
                        "What payment methods do you accept?",
                        "We accept Cash on Delivery (COD), UPI on Delivery (Google Pay, PhonePe, Paytm QR shown by our rider), and online payments via Razorpay (UPI, Credit/Debit cards, Net Banking)."
                ),
                new FaqItemDto(
                        "Pet Nutrition",
                        "How do customer pet profiles and recommendations work?",
                        "You can register your pet's breed and life stage in 'My Pets' to see tailored food recommendations from our Kuravankonam store inventory. Note that we provide nutritional and product advice only; we do not offer medical or veterinary diagnosis."
                )
        );
    }

    /**
     * Generate cryptographically secure 256-bit guest access secret (URL-safe Base64 encoded).
     */
    public static String generateGuestAccessSecret() {
        byte[] bytes = new byte[32]; // 256 bits of cryptographic entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Compute SHA-256 hash of the guest access secret.
     */
    public static String hashGuestAccessSecret(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSecret.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    /**
     * Constant-time comparison between raw guest secret and stored hash.
     * Fails closed if either parameter is missing/null/empty.
     */
    public static boolean verifyGuestSecret(String rawSecret, String storedHash) {
        if (rawSecret == null || rawSecret.isBlank() || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String computedHash = hashGuestAccessSecret(rawSecret);
        if (computedHash == null) {
            return false;
        }
        byte[] a = computedHash.getBytes(StandardCharsets.UTF_8);
        byte[] b = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    @Transactional
    public SupportTicketDto createInquiry(CreateInquiryRequest request, Customer resolvedCustomer) {
        Customer customer = resolvedCustomer;
        String rawGuestSecret = null;
        String guestAccessHash = null;

        if (customer == null) {
            // Never associate unauthenticated inquiries with existing customer records to prevent leaking personal customer data
            String[] nameParts = (request.getName() != null ? request.getName().trim() : "").split(" ", 2);
            String firstName = nameParts[0].isBlank() ? "Guest" : nameParts[0];
            String lastName = nameParts.length > 1 ? nameParts[1] : "";
            customer = customerService.createGuestCustomer(request.getEmail().trim(), request.getPhone(), firstName, lastName);

            // Generate cryptographically secure 256-bit guest access secret
            rawGuestSecret = generateGuestAccessSecret();
            guestAccessHash = hashGuestAccessSecret(rawGuestSecret);
        }

        Order order = null;
        String guestOrderNote = null;
        if (request.getOrderNumber() != null && !request.getOrderNumber().isBlank()) {
            String requestedOrderNum = request.getOrderNumber().trim();
            if (resolvedCustomer != null) {
                // Authenticated customer: only allow attaching orders belonging to authenticated account
                Order foundOrder = orderRepository.findByOrderNumber(requestedOrderNum).orElse(null);
                if (foundOrder != null && foundOrder.getCustomer() != null
                        && foundOrder.getCustomer().getId() != null
                        && foundOrder.getCustomer().getId().equals(resolvedCustomer.getId())) {
                    order = foundOrder;
                } else {
                    log.warn("Authenticated customer {} attempted to attach order {} which does not belong to their account or does not exist",
                            resolvedCustomer.getId(), requestedOrderNum);
                }
            } else {
                // Unauthenticated guest inquiry:
                // Unauthenticated guest inquiries must not attach existing orders solely through email and order-number matching.
                // We preserve legitimate guest support inquiries by noting the referenced order number in the inquiry message,
                // without attaching the persistent Order entity to prevent unauthorized order data disclosure.
                guestOrderNote = "[Guest Referenced Order: " + requestedOrderNum + "] ";
                log.info("Guest support inquiry received referencing order {}; persistent Order entity is not attached to unauthenticated inquiry",
                        requestedOrderNum);
            }
        }

        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String ticketNumber = "TKT-" + dateStr + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SupportTicket ticket = new SupportTicket(
                ticketNumber,
                customer,
                order,
                request.getSubject(),
                request.getPriority()
        );
        if (guestAccessHash != null) {
            ticket.setGuestAccessHash(guestAccessHash);
        }

        String initialMessageText = (guestOrderNote != null ? guestOrderNote : "") + (request.getMessage() != null ? request.getMessage() : "");
        SupportMessage firstMsg = new SupportMessage(
                ticket,
                "CUSTOMER",
                customer.getFirstName() + " " + customer.getLastName(),
                initialMessageText,
                null
        );
        ticket.addMessage(firstMsg);

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Created support ticket {} for customer {}", ticketNumber, customer.getEmail());

        // Send confirmation notification
        String notificationContent = String.format(
                "Dear %s,\n\nYour support inquiry has been received with ticket number #%s.\n" +
                        "Subject: %s\n" +
                        "Our shop team will assist you promptly during store hours.\n\nNOVA PETZLIFE",
                customer.getFirstName(), ticketNumber, request.getSubject()
        );
        notificationService.recordAndDispatch(customer, customer.getEmail(), "EMAIL", "SUPPORT_TICKET_CREATED", "Support Ticket Received: #" + ticketNumber, notificationContent);

        SupportTicketDto result = SupportTicketDto.fromEntity(saved);
        if (rawGuestSecret != null) {
            // Return raw secret ONLY ONCE to original guest inquiry response
            result.setGuestAccessSecret(rawGuestSecret);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SupportTicketDto getTicketByNumber(String ticketNumber) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found or access denied"));
        return SupportTicketDto.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public SupportTicketDto getTicketForCaller(
            String ticketNumber,
            String guestSecret,
            Customer callerCustomer,
            boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found or access denied"));

        if (isAdmin) {
            return SupportTicketDto.fromEntity(ticket);
        }

        boolean isRegisteredCustomerTicket = ticket.getCustomer() != null && ticket.getCustomer().getUser() != null;
        if (isRegisteredCustomerTicket) {
            boolean isOwner = callerCustomer != null && ticket.getCustomer() != null
                    && callerCustomer.getId() != null && callerCustomer.getId().equals(ticket.getCustomer().getId());
            if (!isOwner) {
                // Avoid leaking whether ticket number exists
                throw new ResourceNotFoundException("Support ticket not found or access denied");
            }
            return SupportTicketDto.fromEntity(ticket);
        }

        // Guest ticket: require valid guest access secret
        if (!verifyGuestSecret(guestSecret, ticket.getGuestAccessHash())) {
            // Old guest tickets without a secret and invalid/missing secrets fail closed
            throw new ResourceNotFoundException("Support ticket not found or access denied");
        }

        return SupportTicketDto.fromEntityPublic(ticket);
    }

    @Transactional(readOnly = true)
    public SupportTicketDto getTicketForCaller(String ticketNumber, Customer callerCustomer, boolean isAdmin) {
        return getTicketForCaller(ticketNumber, null, callerCustomer, isAdmin);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getCustomerTickets(Customer customer) {
        if (customer == null) return List.of();
        return ticketRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(SupportTicketDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupportTicketDto> getAllTickets(String status, Pageable pageable) {
        if (status != null && !"ALL".equalsIgnoreCase(status.trim())) {
            return ticketRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable)
                    .map(SupportTicketDto::fromEntity);
        }
        return ticketRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(SupportTicketDto::fromEntity);
    }

    @Transactional
    public SupportTicketDto addMessageToTicket(
            String ticketNumber,
            AddMessageRequest request,
            String guestSecret,
            Customer callerCustomer,
            boolean isAdmin) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found or access denied"));

        boolean isRegisteredCustomerTicket = ticket.getCustomer() != null && ticket.getCustomer().getUser() != null;
        boolean isOwner = callerCustomer != null && ticket.getCustomer() != null
                && callerCustomer.getId() != null && callerCustomer.getId().equals(ticket.getCustomer().getId());

        // Authorization check:
        if (!isAdmin) {
            if (isRegisteredCustomerTicket) {
                if (!isOwner) {
                    throw new ResourceNotFoundException("Support ticket not found or access denied");
                }
            } else {
                // Guest ticket verification: require valid guest secret
                if (!verifyGuestSecret(guestSecret, ticket.getGuestAccessHash())) {
                    throw new ResourceNotFoundException("Support ticket not found or access denied");
                }
            }
        }

        // Enforce sender type: non-admin callers cannot impersonate STAFF
        String senderType = isAdmin
                ? (request.getSenderType() != null && !request.getSenderType().isBlank() ? request.getSenderType().toUpperCase() : "STAFF")
                : "CUSTOMER";

        String senderId;
        if (isAdmin) {
            senderId = request.getSenderId() != null && !request.getSenderId().isBlank() ? request.getSenderId().trim() : "Staff";
        } else if (callerCustomer != null) {
            senderId = callerCustomer.getFirstName() + " " + (callerCustomer.getLastName() != null ? callerCustomer.getLastName() : "").trim();
        } else {
            senderId = request.getSenderId() != null && !request.getSenderId().isBlank() ? request.getSenderId().trim() : "Customer";
        }

        SupportMessage msg = new SupportMessage(
                ticket,
                senderType,
                senderId,
                request.getMessage(),
                request.getAttachmentUrl()
        );
        ticket.addMessage(msg);

        if ("STAFF".equalsIgnoreCase(senderType)) {
            ticket.setStatus("WAITING_FOR_CUSTOMER");
        } else {
            ticket.setStatus("IN_PROGRESS");
        }

        SupportTicket updated = ticketRepository.save(ticket);

        // Apply public / owner / admin DTO privacy mapping
        if (isAdmin || isOwner) {
            return SupportTicketDto.fromEntity(updated);
        }
        return SupportTicketDto.fromEntityPublic(updated);
    }

    @Transactional
    public SupportTicketDto addMessageToTicket(
            String ticketNumber,
            AddMessageRequest request,
            Customer callerCustomer,
            boolean isAdmin) {
        return addMessageToTicket(ticketNumber, request, null, callerCustomer, isAdmin);
    }

    @Transactional
    public SupportTicketDto addMessageToTicket(String ticketNumber, AddMessageRequest request) {
        return addMessageToTicket(ticketNumber, request, null, null, true);
    }

    @Transactional
    public SupportTicketDto updateTicketStatus(Long ticketId, String status, String assignedTo) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Support ticket not found with id: " + ticketId));

        if (status != null && !status.isBlank()) {
            ticket.setStatus(status.toUpperCase().trim());
        }
        if (assignedTo != null && !assignedTo.isBlank()) {
            ticket.setAssignedTo(assignedTo.trim());
        }

        SupportTicket updated = ticketRepository.save(ticket);
        return SupportTicketDto.fromEntity(updated);
    }
}
