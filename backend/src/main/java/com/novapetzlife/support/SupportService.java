package com.novapetzlife.support;

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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

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

        String storeName = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getShopName).orElse("NOVA PETZLIFE");
        String address = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getAddressLine).orElse(contactInfo.address());
        String locality = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCity).orElse("Thiruvananthapuram");
        String city = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCity).orElse("Thiruvananthapuram");
        String state = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getState).orElse("Kerala");
        String country = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getCountry).orElse("India");
        String postalCode = shopOpt.map(com.novapetzlife.delivery.ShopLocation::getPinCode).orElse("695003");
        Double lat = shopOpt.map(s -> s.getLatitude() != null ? s.getLatitude().doubleValue() : 8.528448).orElse(8.528448);
        Double lng = shopOpt.map(s -> s.getLongitude() != null ? s.getLongitude().doubleValue() : 76.953268).orElse(76.953268);
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

    @Transactional
    public SupportTicketDto createInquiry(CreateInquiryRequest request, Customer resolvedCustomer) {
        Customer customer = resolvedCustomer;
        if (customer == null) {
            customer = customerRepository.findByEmail(request.getEmail().toLowerCase().trim()).orElse(null);
            if (customer == null) {
                String[] nameParts = request.getName().trim().split(" ", 2);
                String firstName = nameParts[0];
                String lastName = nameParts.length > 1 ? nameParts[1] : "";
                customer = customerService.createGuestCustomer(request.getEmail(), request.getPhone(), firstName, lastName);
            }
        }

        Order order = null;
        if (request.getOrderNumber() != null && !request.getOrderNumber().isBlank()) {
            order = orderRepository.findByOrderNumber(request.getOrderNumber().trim()).orElse(null);
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

        SupportMessage firstMsg = new SupportMessage(
                ticket,
                "CUSTOMER",
                customer.getFirstName() + " " + customer.getLastName(),
                request.getMessage(),
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

        return SupportTicketDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public SupportTicketDto getTicketByNumber(String ticketNumber) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException("Support ticket not found: " + ticketNumber));
        return SupportTicketDto.fromEntity(ticket);
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
    public SupportTicketDto addMessageToTicket(String ticketNumber, AddMessageRequest request) {
        SupportTicket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException("Support ticket not found: " + ticketNumber));

        SupportMessage msg = new SupportMessage(
                ticket,
                request.getSenderType(),
                request.getSenderId() != null ? request.getSenderId() : "User",
                request.getMessage(),
                request.getAttachmentUrl()
        );
        ticket.addMessage(msg);

        if ("STAFF".equalsIgnoreCase(request.getSenderType())) {
            ticket.setStatus("WAITING_FOR_CUSTOMER");
        } else {
            ticket.setStatus("IN_PROGRESS");
        }

        SupportTicket updated = ticketRepository.save(ticket);
        return SupportTicketDto.fromEntity(updated);
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
