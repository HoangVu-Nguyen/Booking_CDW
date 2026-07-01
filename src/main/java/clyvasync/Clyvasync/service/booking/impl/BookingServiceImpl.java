package clyvasync.Clyvasync.service.booking.impl;

import clyvasync.Clyvasync.constant.ImageConstants;
import clyvasync.Clyvasync.dto.detail.MiniTourInfor;
import clyvasync.Clyvasync.dto.detail.PolicyDetail;
import clyvasync.Clyvasync.dto.detail.TourBookingItemDetail;
import clyvasync.Clyvasync.dto.detail.TourDetail;
import clyvasync.Clyvasync.dto.event.BookingCancelledEvent;
import clyvasync.Clyvasync.dto.event.BookingEvent;
import clyvasync.Clyvasync.dto.event.PaymentRequestMailMessage;
import clyvasync.Clyvasync.dto.request.BookingInitRequest;
import clyvasync.Clyvasync.dto.request.UpdateBookingContactRequest;
import clyvasync.Clyvasync.dto.response.*;
import clyvasync.Clyvasync.enums.booking.BookingStatus;
import clyvasync.Clyvasync.enums.type.NotificationType;
import clyvasync.Clyvasync.enums.type.PaymentStatus;
import clyvasync.Clyvasync.enums.type.TourBookingStatus;
import clyvasync.Clyvasync.exception.AppException;
import clyvasync.Clyvasync.exception.ResultCode;
import clyvasync.Clyvasync.modules.booking.entity.Booking;
import clyvasync.Clyvasync.modules.booking.entity.BookingDetail;
import clyvasync.Clyvasync.modules.homestay.entity.Homestay;
import clyvasync.Clyvasync.modules.homestay.entity.HomestayPolicy;
import clyvasync.Clyvasync.modules.homestay.entity.HomestayRoom;
import clyvasync.Clyvasync.modules.room.RoomRatePlan;
import clyvasync.Clyvasync.modules.tour.entity.Tour;
import clyvasync.Clyvasync.modules.tour.entity.TourAvailability;
import clyvasync.Clyvasync.modules.tour.entity.TourBooking;
import clyvasync.Clyvasync.modules.tour.entity.TourImage;
import clyvasync.Clyvasync.producer.BookingProducer;
import clyvasync.Clyvasync.repository.booking.BookingRepository;
import clyvasync.Clyvasync.service.annotation.IsHomestayOwner;
import clyvasync.Clyvasync.service.booking.BookingDetailService;
import clyvasync.Clyvasync.service.booking.BookingService;
import clyvasync.Clyvasync.service.homestay.HomestayPolicyService;
import clyvasync.Clyvasync.service.homestay.HomestayRoomService;
import clyvasync.Clyvasync.service.homestay.HomestayService;
import clyvasync.Clyvasync.service.notification.NotificationService;
import clyvasync.Clyvasync.service.room.RoomCalendarService;
import clyvasync.Clyvasync.service.room.RoomRatePlanService;
import clyvasync.Clyvasync.service.tour.TourAvailabilityService;
import clyvasync.Clyvasync.service.tour.TourBookingService;
import clyvasync.Clyvasync.service.tour.TourImageService;
import clyvasync.Clyvasync.service.tour.TourService;
import clyvasync.Clyvasync.service.voucher.PointService;
import clyvasync.Clyvasync.service.wallet.WalletTransactionService;
import clyvasync.Clyvasync.repository.voucher.UserVoucherRepository;
import clyvasync.Clyvasync.repository.voucher.VoucherTemplateRepository;
import clyvasync.Clyvasync.repository.voucher.HostVoucherApplyScopeRepository;
import clyvasync.Clyvasync.modules.voucher.entity.UserVoucher;
import clyvasync.Clyvasync.modules.voucher.entity.VoucherTemplate;
import clyvasync.Clyvasync.modules.voucher.entity.HostVoucherApplyScope;
import clyvasync.Clyvasync.enums.offer.VoucherStatus;
import clyvasync.Clyvasync.enums.offer.DiscountType;
import clyvasync.Clyvasync.enums.offer.SponsorType;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.net.URLEncoder;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final HomestayRoomService roomService;
    private final BookingDetailService bookingDetailService;
    private final RoomRatePlanService roomRatePlanService;
    private final TourService tourService;

    private final TourBookingService tourBookingService;
    private final HomestayService homestayService;
    private final TourImageService tourImageService;
    private final HomestayPolicyService homestayPolicyService;
    private final RoomCalendarService roomCalendarService;
    private final TourAvailabilityService tourAvailabilityService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingProducer bookingProducer;
    private final NotificationService notificationService;
    private final PointService pointService;
    private final WalletTransactionService walletTransactionService;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final HostVoucherApplyScopeRepository hostVoucherApplyScopeRepository;

    @Value("${app.frontend.url:https://fe.vunguyen.tokyo}")
    private String frontendUrl;
    @Override
    public boolean existsActiveBooking(Long userId, Long homestayId) {
        return true;
    }

    @Override
    public List<LocalDate> getUnavailableDates(Long roomId, int month, int year) {
        // 1. Xác định ngày đầu và ngày cuối của tháng
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        // 2. Lấy thông tin phòng để biết tổng số lượng căn (quantity)
        HomestayRoom room = roomService.getRoomById(roomId);
        int totalRoomQuantity = room.getQuantity();

        // 3. Lấy tất cả các BookingDetail trùng với tháng này
        List<BookingDetail> overlappingBookings = bookingDetailService.findOverlappingBookings(roomId, startOfMonth, endOfMonth);

        // 4. Tính toán số phòng đã được đặt cho TỪNG NGÀY trong tháng
        Map<LocalDate, Integer> dailyBookedMap = new HashMap<>();

        for (BookingDetail detail : overlappingBookings) {
            LocalDate current = detail.getCheckInDate();
            LocalDate end = detail.getCheckOutDate();

            // Duyệt từ checkInDate đến TRƯỚC checkOutDate (Khách trả phòng thì hôm đó vẫn tính là trống cho khách mới)
            while (current.isBefore(end)) {
                // Chỉ đếm những ngày nằm trong tháng đang xét
                if (!current.isBefore(startOfMonth) && !current.isAfter(endOfMonth)) {
                    dailyBookedMap.put(current, dailyBookedMap.getOrDefault(current, 0) + detail.getQuantity());
                }
                current = current.plusDays(1);
            }
        }

        // 5. Lọc ra những ngày đã FULL phòng (số phòng đã đặt >= tổng số phòng có sẵn)
        List<LocalDate> unavailableDates = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            int bookedQty = dailyBookedMap.getOrDefault(date, 0);

            if (bookedQty >= totalRoomQuantity) {
                unavailableDates.add(date);
            }
        }

        return unavailableDates;
    }

    @Override
    @Transactional
    public BookingInitResponse initBooking(BookingInitRequest request, Long userId) {
        System.out.println(request);

        // 1. TÍNH SỐ ĐÊM & KHÓA PHÒNG
        long nights = java.time.temporal.ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        if (nights <= 0) throw new AppException(ResultCode.INVALID_DATE_RANGE);
        HomestayRoom room = roomService.getRoomById(request.getRoomId());

        int roomRowsUpdated = roomCalendarService.lockRoomRange(
                request.getRoomId(),
                request.getCheckInDate(),
                request.getCheckOutDate(),
                request.getRoomQuantity()
        );
        if (roomRowsUpdated != nights) throw new AppException(ResultCode.ROOM_NOT_AVAILABLE);

        // 2. KHÓA SLOT CHO TOÀN BỘ DANH SÁCH TOUR
        BigDecimal totalTourPrice = BigDecimal.ZERO;

        if (request.getTours() != null && !request.getTours().isEmpty()) {
            for (TourBookingItemDetail tourItem : request.getTours()) {
                int tourRowsUpdated = tourAvailabilityService.deductTourSlots(
                        tourItem.getAvailabilityId(),
                        tourItem.getParticipantCount()
                );

                if (tourRowsUpdated == 0) {
                    throw new AppException(ResultCode.TOUR_NOT_AVAILABLE);
                }

                Tour tour = tourService.findTourById(tourItem.getTourId());
                BigDecimal itemTotal = tour.getPricePerPerson().multiply(BigDecimal.valueOf(tourItem.getParticipantCount()));
                totalTourPrice = totalTourPrice.add(itemTotal);
            }
        }

        // 3. TÍNH TOÁN DÒNG TIỀN (CHUẨN FINTECH)
        String bookingCode = "BK-" + System.currentTimeMillis() % 1000000 + "-" + generateRandomString();
        RoomRatePlan ratePlan = roomRatePlanService.getById(request.getRatePlanId());

        // 3.1. Giá trị gốc của đơn hàng (Tiền phòng + Tiền Tour)
        BigDecimal roomSubtotal = ratePlan.getPrice()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(request.getRoomQuantity()));
        BigDecimal basePrice = roomSubtotal.add(totalTourPrice); // Ví dụ: 1.200.000

        // 3.2. Tiền từ phía KHÁCH HÀNG (Khách chịu 10% phí dịch vụ)
        BigDecimal guestServiceFee = basePrice.multiply(new BigDecimal("0.10")); // 120.000
        BigDecimal finalGrandTotal = basePrice.add(guestServiceFee);             // 1.320.000 (Tổng khách trả)

        // 3.3. Tiền từ phía HOST (Host chịu 5% phí hoa hồng cho nền tảng)
        BigDecimal hostCommission = basePrice.multiply(new BigDecimal("0.05"));  // 60.000
        BigDecimal hostPayout = basePrice.subtract(hostCommission);              // 1.140.000 (Host thực nhận)

        // 3.4. Tổng doanh thu của nền tảng (App thu từ Khách + App thu từ Host)
        BigDecimal platformRevenue = guestServiceFee.add(hostCommission);        // 180.000

        // Điểm thưởng cho khách (1% trên tổng tiền khách trả)
        int pointsEarned = finalGrandTotal
    .divide(new BigDecimal("100000"), RoundingMode.DOWN)
    .intValue();

        // 4. LƯU BOOKING TỔNG
        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .userId(userId)
                .homestayId(request.getHomestayId())

                // Gán dữ liệu dòng tiền cực chuẩn vào DB
                .totalPrice(finalGrandTotal)        // Cột này lưu 1.320.000 (Khách phải trả)
                .taxFee(guestServiceFee)            // Cột này lưu 120.000 (Phí khách trả thêm)
                .hostPayoutAmount(hostPayout)       // Cột này lưu 1.140.000 (Chờ giải ngân cho Host)
                .platformFeeAmount(platformRevenue) // Cột này lưu 180.000 (Tổng doanh thu của Clyvasync)

                .status(BookingStatus.DRAFT)
                .paymentStatus(PaymentStatus.UNPAID)
                .loyaltyPointsEarned(pointsEarned)
                .guestName(request.getGuestName())
                .guestEmail(request.getEmail())
                .guestPhone(request.getPhone())
                .specialRequests(request.getSpecialRequests())
                .build();

        booking = bookingRepository.save(booking);

        // 5. LƯU CHI TIẾT PHÒNG
        BookingDetail detail = BookingDetail.builder()
                .bookingId(booking.getId())
                .roomId(request.getRoomId())
                .ratePlanId(request.getRatePlanId())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .quantity(request.getRoomQuantity())
                .guestCount(request.getGuestCount())
                .unitPrice(ratePlan.getPrice())
                .subtotal(roomSubtotal)
                .build();
        bookingDetailService.save(detail);

        // 6. LƯU CHI TIẾT TỪNG TOUR VÀO BẢNG TOUR_BOOKINGS
        if (request.getTours() != null) {
            for (TourBookingItemDetail tourItem : request.getTours()) {
                Tour tour = tourService.findTourById(tourItem.getTourId());
                BigDecimal itemTotal = tour.getPricePerPerson().multiply(BigDecimal.valueOf(tourItem.getParticipantCount()));

                TourBooking tourBooking = TourBooking.builder()
                        .bookingCode("TR-" + generateRandomString())
                        .tourId(tourItem.getTourId())
                        .userId(userId)
                        .homestayBookingId(booking.getId())
                        .availabilityId(tourItem.getAvailabilityId())
                        .tourDate(tourItem.getTourDate())
                        .participantCount(tourItem.getParticipantCount())
                        .totalPrice(itemTotal)
                        .status(TourBookingStatus.DRAFT)
                        .paymentStatus(PaymentStatus.UNPAID)
                        .build();

                tourBookingService.save(tourBooking);
            }
        }

        // 7. PUBLISH EVENT ĐỒNG BỘ LỊCH
        Map<String, Object> calendarPayload = Map.of(
                "type", "CALENDAR_SYNC",
                "roomId", request.getRoomId(),
                "action", "LOCK_DATES",
                "checkIn", request.getCheckInDate().toString(),
                "checkOut", request.getCheckOutDate().toString()
        );

        eventPublisher.publishEvent(new BookingEvent(this, null, calendarPayload));

        boolean instantBookFlag = room.getIsInstantBook() != null ? room.getIsInstantBook() : true;
        return new BookingInitResponse(bookingCode, booking.getId(), instantBookFlag);
    }

    @Override
    public BookingDetailsResponse getBookingDetailsByCode(String bookingCode) {
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));

        BookingDetail detail = bookingDetailService.findBookingDetailByBookingId(booking.getId());
        HomestayResponse homestayResponse = homestayService.getById(booking.getHomestayId());
        HomestayRoom homestayRoom = roomService.getRoomById(detail.getRoomId());
        HomestayPolicy homestayPolicy = homestayPolicyService.getHomestayPolicyByHomestayId(booking.getHomestayId());

        List<TourBooking> tourBookings = tourBookingService.findAllByHomestayBookingId(booking.getId());
        List<TourDetail> tourDetails = List.of();
        BigDecimal tourSubtotal = BigDecimal.ZERO;
        Boolean instantBookFlag = homestayRoom.getIsInstantBook() != null ? homestayRoom.getIsInstantBook() : true;

        if (!tourBookings.isEmpty()) {
            // GOM TẤT CẢ ID TOUR LẠI (Batching)
            List<Long> tourIds = tourBookings.stream().map(TourBooking::getTourId).distinct().toList();

            // CHỈ 1 QUERY lấy toàn bộ thông tin Tour lõi lên Map (Map<Id, Tour>)
            Map<Long, Tour> tourMap = tourService.findAllByIds(tourIds).stream()
                    .collect(Collectors.toMap(Tour::getId, t -> t));

            // CHỈ 1 QUERY lấy toàn bộ ảnh đại diện Tour lên Map (Map<TourId, ImageUrl>)
            Map<Long, String> tourImageMap = tourImageService.getPrimaryImagesByTourIds(tourIds);

            // Map sang DTO từ bộ nhớ (In-memory mapping - Không đụng vào DB nữa)
            tourDetails = tourBookings.stream().map(tb -> {
                Tour tourCore = tourMap.get(tb.getTourId());
                return TourDetail.builder()
                        .tourBookingId(tb.getId())
                        .tourBookingCode(tb.getBookingCode())
                        .tourName(tourCore != null ? tourCore.getName() : "N/A")
                        .tourImage(tourImageMap.getOrDefault(tb.getTourId(), ImageConstants.TOUR_DEFAULT))
                        .tourDate(tb.getTourDate())
                        .participantCount(tb.getParticipantCount())
                        .totalPrice(tb.getTotalPrice())
                        .build();
            }).toList();

            // Tính tổng tiền Tour từ mảng đã lấy
            tourSubtotal = tourDetails.stream()
                    .map(TourDetail::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 4. ĐÓNG GÓI RESPONSE
        long totalNights = java.time.temporal.ChronoUnit.DAYS.between(detail.getCheckInDate(), detail.getCheckOutDate());

        return BookingDetailsResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status(booking.getStatus())
                .paymentStatus(booking.getPaymentStatus())
                .isApproved(booking.isApproved())
                .specialRequests(booking.getSpecialRequests())
                .loyaltyPointsEarned(booking.getLoyaltyPointsEarned())
                .homestayId(homestayResponse.getId())
                .homestayName(homestayResponse.getName())
                .homestayAddress(homestayResponse.getAddressDetail())
                .roomName(homestayRoom.getName())
                .isInstantBook(instantBookFlag)
                .roomImage(homestayRoom.getImageUrl())

                .checkInDate(detail.getCheckInDate())
                .checkOutDate(detail.getCheckOutDate())
                .totalNights(totalNights)
                .roomQuantity(detail.getQuantity())
                .guestCount(detail.getGuestCount())

                .tours(tourDetails) // Trả về mảng Tour
                .policy(mapToPolicyDto(homestayPolicy))

                .roomSubtotal(detail.getSubtotal())
                .tourSubtotal(tourSubtotal)
                .taxFee(booking.getTaxFee())
                .grandTotal(booking.getTotalPrice())
                .build();
    }

    @Override
    public Booking getBookingByCode(String bookingCode) {
        return bookingRepository.findBookingByBookingCode(bookingCode).orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));
    }

    @Override
    public List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Booking findByBookingCodeAndUserId(String bookingCode, Long currentUserId) {
        return bookingRepository.findByBookingCodeAndUserId(bookingCode,currentUserId).orElseThrow(()-> new AppException(ResultCode.BOOKING_NOT_FOUND));
    }

    @Override
    public List<Booking> findAllByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, BookingStatus status) {
        return bookingRepository.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId,status);
    }

    @Override
    public List<Booking> findBookingsReadyForEscrowRelease(LocalDate targetDate) {
        return bookingRepository.findBookingsReadyForEscrowRelease(targetDate);
    }

    @Override
    public List<HostBookingItemResponse> getHostBookings(Long ownerId) {
        List<Homestay> hostHomestays = Optional
                .ofNullable(homestayService.findByOwnerId(ownerId))
                .orElseGet(List::of);

        if (hostHomestays.isEmpty()) {
            return List.of();
        }

        List<Long> homestayIds = hostHomestays.stream()
                .map(Homestay::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (homestayIds.isEmpty()) {
            return List.of();
        }

        Map<Long, String> homestayNameMap = new HashMap<>();
        for (Homestay home : hostHomestays) {
            if (home.getId() != null) {
                homestayNameMap.put(home.getId(), home.getName() != null ? home.getName() : "N/A");
            }
        }

        List<Booking> bookings = Optional
                .ofNullable(bookingRepository.findByHomestayIdInOrderByCreatedAtDesc(homestayIds))
                .orElseGet(List::of);

        if (bookings.isEmpty()) {
            return List.of();
        }

        List<Long> bookingIds = bookings.stream()
                .map(Booking::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (bookingIds.isEmpty()) {
            return List.of();
        }

        List<BookingDetail> details = Optional
                .ofNullable(bookingDetailService.findByBookingIdIn(bookingIds))
                .orElseGet(List::of);

        Map<Long, BookingDetail> detailMap = details.stream()
                .filter(d -> d.getBookingId() != null)
                .collect(Collectors.toMap(
                        BookingDetail::getBookingId,
                        Function.identity(),
                        (d1, d2) -> d1
                ));

        List<TourBooking> allTourBookings = Optional
                .ofNullable(tourBookingService.findByHomestayBookingIdIn(bookingIds))
                .orElseGet(List::of);

        Map<Long, List<TourBooking>> bookingToursMap = allTourBookings.stream()
                .filter(tb -> tb.getHomestayBookingId() != null)
                .collect(Collectors.groupingBy(TourBooking::getHomestayBookingId));

        List<Long> coreTourIds = allTourBookings.stream()
                .map(TourBooking::getTourId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Tour> tours = coreTourIds.isEmpty()
                ? List.of()
                : Optional.ofNullable(tourService.findAllByIds(coreTourIds))
                .orElseGet(List::of);

        Map<Long, Tour> tourMap = tours.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(
                        Tour::getId,
                        Function.identity(),
                        (t1, t2) -> t1
                ));

        Map<Long, String> tourImageMap = coreTourIds.isEmpty()
                ? Map.of()
                : Optional.ofNullable(tourImageService.getPrimaryImagesByTourIds(coreTourIds))
                .orElseGet(Map::of);

        List<TourAvailability> tourAvailabilities = coreTourIds.isEmpty()
                ? List.of()
                : Optional.ofNullable(tourAvailabilityService.findByIdIn(coreTourIds))
                .orElseGet(List::of);

        Map<Long, TourAvailability> tourAvailabilityMap = tourAvailabilities.stream()
                .filter(t -> t.getTourId() != null)
                .collect(Collectors.toMap(
                        TourAvailability::getTourId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<Long> roomIds = details.stream()
                .map(BookingDetail::getRoomId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<HomestayRoom> rooms = roomIds.isEmpty()
                ? List.of()
                : Optional.ofNullable(roomService.findAllByIdIn(roomIds))
                .orElseGet(List::of);

        Map<Long, String> roomNameMap = new HashMap<>();
        Map<Long, String> roomImageMap = new HashMap<>();

        for (HomestayRoom room : rooms) {
            if (room.getId() == null) {
                continue;
            }

            roomNameMap.put(
                    room.getId(),
                    room.getName() != null ? room.getName() : "N/A"
            );

            if (room.getImageUrl() != null && !room.getImageUrl().isBlank()) {
                roomImageMap.put(room.getId(), room.getImageUrl());
            }
        }

        return bookings.stream()
                .map(booking -> {
                    BookingDetail detail = detailMap.get(booking.getId());

                    if (detail == null) {
                        return null;
                    }

                    LocalDate checkInDate = detail.getCheckInDate();
                    LocalDate checkOutDate = detail.getCheckOutDate();

                    long nights = 0;
                    if (checkInDate != null && checkOutDate != null) {
                        nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
                    }

                    String uiStatus = "PENDING";
                    BigDecimal paidAmount = BigDecimal.ZERO;

                    if (PaymentStatus.PAID.equals(booking.getPaymentStatus())) {
                        paidAmount = booking.getTotalPrice() != null
                                ? booking.getTotalPrice()
                                : BigDecimal.ZERO;
                    }

                    if (BookingStatus.DRAFT.equals(booking.getStatus())) {
                        uiStatus = "DRAFT";
                    } else if (BookingStatus.PENDING.equals(booking.getStatus())) {
                        uiStatus = "PENDING";
                    } else if (BookingStatus.CONFIRMED.equals(booking.getStatus())) {
                        uiStatus = "CONFIRMED";
                    } else if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
                        uiStatus = "CANCELLED";
                    } else if (BookingStatus.AWAITING_PAYMENT.equals(booking.getStatus())) {
                        uiStatus = "AWAITING_PAYMENT";
                    }

                    List<MiniTourInfor> comboTours = new ArrayList<>();

                    List<TourBooking> bookingTours = bookingToursMap.getOrDefault(
                            booking.getId(),
                            List.of()
                    );

                    for (TourBooking tb : bookingTours) {
                        if (tb.getTourId() == null) {
                            continue;
                        }

                        Tour coreTour = tourMap.get(tb.getTourId());

                        if (coreTour == null) {
                            continue;
                        }

                        String tourName = coreTour.getName() != null
                                ? coreTour.getName()
                                : "Tour";

                        String tourImg = tourImageMap.getOrDefault(
                                tb.getTourId(),
                                ImageConstants.TOUR_DEFAULT
                        );

                        BigDecimal pricePerPerson = coreTour.getPricePerPerson() != null
                                ? coreTour.getPricePerPerson()
                                : BigDecimal.ZERO;

                        int participantCount = tb.getParticipantCount() != null
                                ? tb.getParticipantCount()
                                : 0;

                        BigDecimal totalTourPrice = pricePerPerson.multiply(
                                BigDecimal.valueOf(participantCount)
                        );

                        TourAvailability availability = tourAvailabilityMap.get(coreTour.getId());

                        LocalDate startDate = availability != null
                                ? availability.getStartDate()
                                : null;

                        LocalTime startTime = availability != null
                                ? availability.getStartTime()
                                : null;

                        comboTours.add(new MiniTourInfor(
                                tourName,
                                tourImg,
                                totalTourPrice,
                                participantCount,
                                startDate,
                                startTime
                        ));
                    }

                    String displayGuestName = booking.getGuestName() != null && !booking.getGuestName().isBlank()
                            ? booking.getGuestName()
                            : "Khách đang điền...";

                    String displayPhone = booking.getGuestPhone() != null && !booking.getGuestPhone().isBlank()
                            ? booking.getGuestPhone()
                            : "---";

                    String displayEmail = booking.getGuestEmail() != null && !booking.getGuestEmail().isBlank()
                            ? booking.getGuestEmail()
                            : "---";

                    String guestAvatarName = URLEncoder.encode(
                            displayGuestName,
                            StandardCharsets.UTF_8
                    );

                    LocalDateTime checkInDateTime = checkInDate != null
                            ? checkInDate.atTime(14, 0)
                            : null;

                    LocalDateTime checkOutDateTime = checkOutDate != null
                            ? checkOutDate.atTime(12, 0)
                            : null;

                    return HostBookingItemResponse.builder()
                            .bookingCode(booking.getBookingCode())
                            .guestName(displayGuestName)
                            .guestPhone(displayPhone)
                            .guestEmail(displayEmail)
                            .guestAvatar("https://ui-avatars.com/api/?name=" + guestAvatarName + "&background=random")

                            .homestayName(homestayNameMap.getOrDefault(booking.getHomestayId(), "N/A"))
                            .roomName(roomNameMap.getOrDefault(detail.getRoomId(), "N/A"))
                            .roomImage(roomImageMap.getOrDefault(detail.getRoomId(), ImageConstants.ROOM_DEFAULT))

                            .adults(detail.getGuestCount() != null ? detail.getGuestCount() : 0)
                            .children(0)

                            .checkInDate(checkInDateTime)
                            .checkOutDate(checkOutDateTime)
                            .nights(nights)

                            .source("Website Trực tiếp")
                            .totalPrice(booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO)
                            .includedTours(comboTours)
                            .paidAmount(paidAmount)
                            .status(uiStatus)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional
    public void updateContactInfo(String bookingCode, UpdateBookingContactRequest request) {
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));
        BookingDetail bookingDetail  = bookingDetailService.findBookingDetailByBookingId(booking.getId());
        HomestayRoom homestayRoom = roomService.getRoomById(bookingDetail.getRoomId());

        booking.setGuestName(request.getGuestName());
        booking.setGuestPhone(request.getPhone());
        booking.setGuestEmail(request.getEmail());
        booking.setSpecialRequests(request.getSpecialRequests());

        boolean needsApproval = !homestayRoom.getIsInstantBook() && !booking.isApproved();

        if (needsApproval) {
            booking.setStatus(BookingStatus.PENDING);
            bookingRepository.save(booking);

            // BẮN THÔNG BÁO CHO HOST
            // Lưu ý: bác cần lấy hostId từ homestay hoặc booking
            Long hostId = homestayService.findById(booking.getHomestayId()).getOwnerId();

            notificationService.sendNotification(
                    hostId,
                    NotificationType.BOOKING_REQUEST,
                    "Yêu cầu đặt phòng mới",
                    String.format("Khách hàng %s vừa cập nhật thông tin cho yêu cầu đặt phòng %s. Vui lòng kiểm tra và duyệt.",
                            request.getGuestName(), bookingCode),
                    Map.of("bookingId", booking.getId(), "bookingCode", bookingCode)
            );
        } else {
            bookingRepository.save(booking);
        }

    }

    @Override
    @IsHomestayOwner
    @Transactional
    public void approveBooking(String bookingCode, Long hostId) {
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));
        if (!BookingStatus.PENDING.equals(booking.getStatus())) {
            throw new AppException(ResultCode.INVALID_STATUS);
        }
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        booking.setApproved(true);
        bookingRepository.save(booking);
        String checkoutLink = frontendUrl + "/checkout/" + booking.getBookingCode();
        Homestay homestay = homestayService.findById(booking.getHomestayId());
        BookingDetail detail = bookingDetailService.findBookingDetailByBookingId(booking.getId());
        HomestayRoom room = roomService.getRoomById(detail.getRoomId());

        // 1. Đóng gói DTO
        PaymentRequestMailMessage mailMsg = PaymentRequestMailMessage.builder()
                .bookingCode(booking.getBookingCode())
                .guestName(booking.getGuestName())
                .guestEmail(booking.getGuestEmail())
                .homestayName(homestay != null ? homestay.getName() : "Clyvasync Homestay")
                .roomName(room != null ? room.getName() : "Phòng tiêu chuẩn")
                .grandTotal(booking.getTotalPrice())
                .checkoutUrl(checkoutLink)
                .build();

        // 2. GỌI PRODUCER (Mã nguồn lúc này cực kỳ Clean!)
        bookingProducer.sendPaymentRequestMail(mailMsg);
        notificationService.sendNotification(
                booking.getUserId(), // ID người đặt
                NotificationType.BOOKING_AWAITING_PAYMENT,
                "Đặt phòng đã được duyệt!",
                String.format("Homestay %s đã duyệt yêu cầu của bạn. Vui lòng thanh toán để giữ phòng.", mailMsg.getHomestayName()),
                Map.of("bookingId", booking.getId(), "bookingCode", booking.getBookingCode())
        );
    }

    @Override
    @Transactional
    public void rejectBooking(String bookingCode, String rejectReason, Long hostId) {
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));

        if (!BookingStatus.PENDING.equals(booking.getStatus())) {
            throw new AppException(ResultCode.INVALID_STATUS);
        }

        // 1. Đổi trạng thái Booking thành Hủy
        booking.setStatus(BookingStatus.CANCELLED);
        // Lưu lý do hủy vào bảng Booking (Bác có thể tạo thêm cột cancel_reason ở DB)
        // booking.setCancelReason(rejectReason);
        bookingRepository.save(booking);

        // 2. GIẢI PHÓNG LỊCH PHÒNG (Unlock Room Calendar)
        BookingDetail detail = bookingDetailService.findBookingDetailByBookingId(booking.getId());
        roomCalendarService.unlockRoomRange(
                detail.getRoomId(),
                detail.getCheckInDate(),
                detail.getCheckOutDate(),
                detail.getQuantity()
        );

        // 2.5. HOÀN LẠI VOUCHER (NẾU CÓ)
        restoreUserVoucher(booking);

        // 3. GIẢI PHÓNG SLOT TOUR (Restore Tour Availability)
        List<TourBooking> tourBookings = tourBookingService.findAllByHomestayBookingId(booking.getId());
        if (tourBookings != null && !tourBookings.isEmpty()) {
            for (TourBooking tb : tourBookings) {
                tourAvailabilityService.releaseTourSlots(tb.getAvailabilityId(), tb.getParticipantCount());

                // Đổi trạng thái của TourBooking thành CANCELLED luôn
                tb.setStatus(TourBookingStatus.CANCELLED);
                tourBookingService.save(tb);
            }
        }

        // 4. BẮN SOCKET ĐỒNG BỘ CALENDAR & GỬI EMAIL XIN LỖI KHÁCH
        Map<String, Object> calendarPayload = Map.of(
                "type", "CALENDAR_SYNC",
                "roomId", detail.getRoomId(),
                "action", "UNLOCK_DATES" // Báo UI mở lại lịch
        );
        eventPublisher.publishEvent(new BookingEvent(this, null, calendarPayload));
        notificationService.sendNotification(
                booking.getUserId(),
                NotificationType.BOOKING_CANCELLED,
                "Yêu cầu đặt phòng bị từ chối",
                String.format("Rất tiếc, yêu cầu đặt phòng %s đã bị từ chối. Lý do: %s", booking.getBookingCode(), rejectReason),
                Map.of("bookingId", booking.getId(), "bookingCode", booking.getBookingCode())
        );
    }

    @Override
    public List<Booking> findAllExpired(OffsetDateTime draftThreshold, OffsetDateTime paymentThreshold, BookingStatus draftStatus, BookingStatus paymentStatus) {
        return bookingRepository.findAllExpired(draftThreshold,paymentThreshold,draftStatus,paymentStatus);
    }

    @Override
    public BigDecimal sumRevenueByHomestays(List<Long> homeIds, OffsetDateTime startOfThisMonth) {
        return bookingRepository.sumRevenueByHomestays(homeIds,startOfThisMonth);
    }

    @Override
    public BigDecimal sumRevenueByHomestaysAndDateRange(List<Long> homestayIds, OffsetDateTime startDate, OffsetDateTime endDate) {
        return bookingRepository.sumRevenueByHomestaysAndDateRange(homestayIds,startDate,endDate);
    }

    @Override
    @Transactional
    public void cancelBooking(String bookingCode, Long userId) {
        log.info("[CANCEL-BOOKING] Khởi chạy luồng hủy đơn bởi UserId: {}, BookingCode: {}", userId, bookingCode);

        // 1. LOCK & FETCH: Sử dụng Pessimistic Lock chống Race Condition
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));

        // 2. AUTHORIZATION: Bảo mật tuyệt đối
        if (!booking.getUserId().equals(userId)) {
            log.warn("[CANCEL-BOOKING SECURITY] UserId {} cố tình hủy đơn {} không thuộc sở hữu!", userId, bookingCode);
            throw new AppException(ResultCode.UNAUTHORIZED_ACTION);
        }

        // 3. IDEMPOTENCY & STATE MACHINE VALIDATION
        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {
            log.info("[CANCEL-BOOKING] Đơn hàng {} đã ở trạng thái HỦY trước đó (Idempotent).", bookingCode);
            return;
        }
        if (BookingStatus.COMPLETED.equals(booking.getStatus())) {
            throw new AppException(ResultCode.CANNOT_CANCEL_COMPLETED_BOOKING);
        }

        BookingStatus oldStatus = booking.getStatus();
        BookingDetail detail = bookingDetailService.findBookingDetailByBookingId(booking.getId());

        // 4. FINANCIAL PROCESSING: Xử lý hoàn tiền và ví chủ nhà (CHỈ KHI ĐÃ THANH TOÁN)
        if (PaymentStatus.PAID.equals(booking.getPaymentStatus())) {
            log.info("[CANCEL-BOOKING-FINANCE] Bắt đầu tính toán hoàn tiền cho đơn {}", bookingCode);

            // Gọi sang Wallet Service để xử lý giao dịch ví, trả về trạng thái Payment mới
            PaymentStatus newPaymentStatus = walletTransactionService.processCancellationRefund(
                    booking.getHomestayId(),
                    booking.getId(),
                    bookingCode,
                    booking.getTotalPrice(),
                    detail
            );
            booking.setPaymentStatus(newPaymentStatus);

            // 4.5 TRỪ ĐIỂM THƯỞNG NẾU ĐƠN BỊ HỦY SAU KHI ĐÃ THANH TOÁN (THU HỒI ĐIỂM)
            try {
                Integer pointsToDeduct = booking.getTotalPrice().intValue() / 100000;
                if (pointsToDeduct > 0) {
                    pointService.deductPointsForBookingCancellation(
                        userId, 
                        pointsToDeduct, 
                        booking.getId(), 
                        "Thu hồi điểm do hủy đơn đặt phòng #" + bookingCode
                    );
                    log.info("Đã thu hồi {} điểm của user {} do hủy booking {}", pointsToDeduct, userId, bookingCode);
                }
            } catch (Exception e) {
                log.error("Lỗi khi thu hồi điểm thưởng do hủy booking {}: {}", bookingCode, e.getMessage());
            }
        }

        // 5. STATE TRANSITION: Cập nhật trạng thái
        System.out.println("Chay khong");
        booking.setStatus(BookingStatus.CANCELLED);
        System.out.println("Chay khong");
        bookingRepository.saveAndFlush(booking);

        // 6. RESOURCE CLEANUP (ROOM): Giải phóng lịch phòng homestay
        roomCalendarService.unlockRoomRange(
                detail.getRoomId(),
                detail.getCheckInDate(),
                detail.getCheckOutDate(),
                detail.getQuantity()
        );

        // 6.5. HOÀN LẠI VOUCHER
        restoreUserVoucher(booking);

        // 7. RESOURCE CLEANUP (TOUR): Giải phóng slot tour đi kèm
        List<TourBooking> tourBookings = tourBookingService.findAllByHomestayBookingId(booking.getId());
        if (tourBookings != null && !tourBookings.isEmpty()) {
            tourAvailabilityService.releaseTourSlotsBatch(tourBookings);
            tourBookingService.cancelAllByHomestayBookingId(booking.getId());
        }


        // 8. DISPATCH EVENT: Phát tín hiệu sự kiện ra toàn hệ thống (Gửi mail, push notification...)
        eventPublisher.publishEvent(new BookingCancelledEvent(booking, oldStatus, "GUEST",previewCancelBooking(bookingCode,userId)));
        log.info("[CANCEL-BOOKING SUCCESS] Đơn hàng {} đã hủy thành công trên DB, đang chờ commit.", bookingCode);
    }

    private void restoreUserVoucher(Booking booking) {
        if (booking.getUserVoucherId() != null) {
            userVoucherRepository.findByIdAndUserId(booking.getUserVoucherId(), booking.getUserId())
                    .ifPresent(uv -> {
                        uv.setStatus(VoucherStatus.AVAILABLE);
                        uv.setUsedAt(null);
                        uv.setUsedOnBookingId(null);
                        userVoucherRepository.save(uv);
                    });
        }
    }

    @Override
    @Transactional
    public void applyVoucherToBooking(Booking booking, Long userVoucherId) {
        if (userVoucherId == null) return;
        
        if (booking.getUserVoucherId() != null) {
            if (booking.getUserVoucherId().equals(userVoucherId)) {
                return;
            }
            throw new RuntimeException("Một mã giảm giá đã được áp dụng cho đơn hàng này. Vui lòng hủy đơn và tạo lại để đổi mã.");
        }

        UserVoucher appliedVoucher = userVoucherRepository.findByIdAndUserId(userVoucherId, booking.getUserId())
                .orElseThrow(() -> new AppException(ResultCode.DATA_NOT_FOUND));

        if (!VoucherStatus.AVAILABLE.equals(appliedVoucher.getStatus())) {
            throw new RuntimeException("Mã giảm giá này không khả dụng hoặc đã được sử dụng.");
        }

        VoucherTemplate template = voucherTemplateRepository.findById(appliedVoucher.getTemplateId())
                .orElseThrow(() -> new AppException(ResultCode.DATA_NOT_FOUND));

        if (template.getValidUntil() != null && template.getValidUntil().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Mã giảm giá này đã hết hạn sử dụng.");
        }

        if (template.getTotalUsageLimit() != null && template.getCurrentUsageCount() != null && 
            template.getCurrentUsageCount() >= template.getTotalUsageLimit()) {
            throw new RuntimeException("Mã giảm giá này đã vượt quá số lượt sử dụng tối đa.");
        }

        // Tính lại basePrice vì totalPrice hiện tại là (basePrice + taxFee)
        // Mà taxFee = basePrice * 10% => totalPrice = basePrice * 1.1
        // Do đó basePrice = totalPrice - taxFee
        BigDecimal originalGrandTotal = booking.getTotalPrice(); 
        BigDecimal basePrice = originalGrandTotal.subtract(booking.getTaxFee());

        if (template.getMinOrderValue() != null && basePrice.compareTo(template.getMinOrderValue()) < 0) {
            throw new RuntimeException("Đơn hàng chưa đạt giá trị tối thiểu " + template.getMinOrderValue() + " để áp dụng mã giảm giá này.");
        }

        if (SponsorType.HOST.equals(template.getSponsorType()) || SponsorType.HOST_SPONSORED.equals(template.getSponsorType())) {
            List<HostVoucherApplyScope> scopes = hostVoucherApplyScopeRepository.findByVoucherId(template.getId());
            if (!scopes.isEmpty()) {
                boolean isValidHomestay = scopes.stream().anyMatch(scope -> scope.getHomestayId().equals(booking.getHomestayId()));
                if (!isValidHomestay) {
                    throw new RuntimeException("Mã giảm giá này không áp dụng cho Homestay bạn đang đặt.");
                }
            }
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (DiscountType.FIXED_AMOUNT.equals(template.getDiscountType())) {
            discountAmount = template.getDiscountValue();
        } else if (DiscountType.PERCENTAGE.equals(template.getDiscountType())) {
            discountAmount = basePrice.multiply(template.getDiscountValue()).divide(new BigDecimal("100"), RoundingMode.HALF_UP);
            if (template.getMaxDiscount() != null && discountAmount.compareTo(template.getMaxDiscount()) > 0) {
                discountAmount = template.getMaxDiscount();
            }
        }
        
        // Capping
        if (discountAmount.compareTo(originalGrandTotal) > 0) {
            discountAmount = originalGrandTotal;
        }

        booking.setDiscountAmount(discountAmount);
        booking.setUserVoucherId(userVoucherId);
        
        if (SponsorType.HOST.equals(template.getSponsorType()) || SponsorType.HOST_SPONSORED.equals(template.getSponsorType())) {
            // Không để Host Payout bị âm
            if (discountAmount.compareTo(booking.getHostPayoutAmount()) > 0) {
                discountAmount = booking.getHostPayoutAmount();
            }
            booking.setHostDiscountAmount(discountAmount);
            booking.setHostPayoutAmount(booking.getHostPayoutAmount().subtract(discountAmount));
        } else {
            // Không để Platform Fee bị âm
            if (discountAmount.compareTo(booking.getPlatformFeeAmount()) > 0) {
                discountAmount = booking.getPlatformFeeAmount();
            }
            booking.setPlatformDiscountAmount(discountAmount);
            booking.setPlatformFeeAmount(booking.getPlatformFeeAmount().subtract(discountAmount));
        }

        booking.setTotalPrice(originalGrandTotal.subtract(discountAmount));
        
        // Recalculate loyalty points
        int pointsEarned = booking.getTotalPrice()
                .divide(new BigDecimal("100000"), RoundingMode.DOWN)
                .intValue();
        booking.setLoyaltyPointsEarned(pointsEarned);

        bookingRepository.save(booking);
        
        appliedVoucher.setStatus(VoucherStatus.USED);
        appliedVoucher.setUsedAt(OffsetDateTime.now());
        appliedVoucher.setUsedOnBookingId(booking.getId());
        userVoucherRepository.save(appliedVoucher);

        template.setCurrentUsageCount((template.getCurrentUsageCount() != null ? template.getCurrentUsageCount() : 0) + 1);
        voucherTemplateRepository.save(template);
    }
    
    // Hàm phụ để code nhìn gọn hơn
    private PolicyDetail mapToPolicyDto(HomestayPolicy policy) {
        return PolicyDetail.builder()
                .checkInTime(policy.getCheckInTime())
                .checkOutTime(policy.getCheckOutTime())
                .lateCheckInInstruction(policy.getLateCheckInInstruction())
                .allowsPets(policy.getAllowsPets())
                .allowsSmoking(policy.getAllowsSmoking())
                .allowsParties(policy.getAllowsParties())
                .build();
    }

    private String generateRandomString() {
        return java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
    private BigDecimal calculateRefundAmount(List<BookingDetail> details) {
        BigDecimal totalRefund = BigDecimal.ZERO;
        LocalDate today = LocalDate.now(); // Lưu ý: Nên dùng TimeZone của user hoặc hệ thống cho chuẩn

        for (BookingDetail detail : details) {
            RoomRatePlan ratePlan = roomRatePlanService.getById(detail.getRatePlanId());


            // 1. Kiểm tra chính sách gói giá: Nếu là Non-refundable -> Không hoàn đồng nào
            if (Boolean.TRUE.equals(ratePlan.getIsNonRefundable())) {
                continue;
            }

            // 2. Tính số ngày từ lúc hủy đến ngày Check-in
            long daysUntilCheckIn = ChronoUnit.DAYS.between(today, detail.getCheckInDate());
            BigDecimal subtotal = detail.getSubtotal();

            // 3. Áp dụng các mốc hoàn tiền (Tùy chỉnh theo luật của bạn)
            if (daysUntilCheckIn >= 7) {
                // Hủy sớm trước 7 ngày: Hoàn 100%
                totalRefund = totalRefund.add(subtotal);
            } else if (daysUntilCheckIn >= 3) {
                // Hủy trước 3 - 6 ngày: Hoàn 50%
                BigDecimal refund50 = subtotal.multiply(new BigDecimal("0.50"));
                totalRefund = totalRefund.add(refund50);
            } else {
                // Hủy quá sát ngày (dưới 3 ngày) hoặc đã check-in: Hoàn 0%
                // Không cộng thêm gì vào totalRefund
            }
        }

        return totalRefund;
    }
    @Transactional(readOnly = true)
    @Override
    public CancelPreviewResponse previewCancelBooking(String bookingCode, Long userId) {
        // 1. Validate cơ bản
        Booking booking = bookingRepository.findBookingByBookingCode(bookingCode)
                .orElseThrow(() -> new AppException(ResultCode.BOOKING_NOT_FOUND));

        if (!booking.getUserId().equals(userId)) {
            throw new AppException(ResultCode.UNAUTHORIZED_ACTION);
        }

        // Nếu chưa thanh toán thì không có gì để hoàn
        if (!PaymentStatus.PAID.equals(booking.getPaymentStatus())) {
            return CancelPreviewResponse.builder()
                    .bookingCode(bookingCode)
                    .totalPaid(BigDecimal.ZERO)
                    .refundAmount(BigDecimal.ZERO)
                    .penaltyFee(BigDecimal.ZERO)
                    .refundPolicyMessage("Đơn hàng chưa thanh toán, bạn có thể hủy miễn phí.")
                    .build();
        }

        // 2. Tính toán dựa trên Detail & Policy
        BookingDetail detail = bookingDetailService.findBookingDetailByBookingId(booking.getId());
        RoomRatePlan ratePlan = roomRatePlanService.getById(detail.getRatePlanId());

        BigDecimal totalPaid = booking.getTotalPrice();
        BigDecimal refundAmount = BigDecimal.ZERO;
        String policyMsg = "Gói giá không hoàn tiền (Non-refundable).";

        if (Boolean.FALSE.equals(ratePlan.getIsNonRefundable())) {
            long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), detail.getCheckInDate());

            if (daysUntilCheckIn >= 7) {
                refundAmount = totalPaid;
                policyMsg = "Hủy trước 7 ngày, bạn được hoàn 100% số tiền.";
            } else if (daysUntilCheckIn >= 3) {
                refundAmount = totalPaid.multiply(new BigDecimal("0.50"));
                policyMsg = "Hủy trước 3-6 ngày, bạn bị thu phí phạt 50%.";
            } else {
                policyMsg = "Hủy quá sát ngày (dưới 3 ngày), không được hoàn tiền.";
            }
        }

        BigDecimal penaltyFee = totalPaid.subtract(refundAmount);

        return CancelPreviewResponse.builder()
                .bookingCode(bookingCode)
                .totalPaid(totalPaid)
                .refundAmount(refundAmount)
                .penaltyFee(penaltyFee)
                .refundPolicyMessage(policyMsg)
                .build();
    }

}
