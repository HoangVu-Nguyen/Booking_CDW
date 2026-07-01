package clyvasync.Clyvasync.service.homestay.impl;

import clyvasync.Clyvasync.dto.detail.PropertyStats;
import clyvasync.Clyvasync.dto.projection.BookingTimelineProjection;
import clyvasync.Clyvasync.dto.record.AiSearchRequest;
import clyvasync.Clyvasync.dto.request.*;
import clyvasync.Clyvasync.dto.response.*;
import clyvasync.Clyvasync.dto.summary.HomestayRoomSummary;
import clyvasync.Clyvasync.enums.booking.BookingStatus;
import clyvasync.Clyvasync.enums.homestay.HomestayStatus;
import clyvasync.Clyvasync.enums.media.MediaStatus;
import clyvasync.Clyvasync.exception.AppException;
import clyvasync.Clyvasync.exception.ResultCode;
import clyvasync.Clyvasync.mapper.homestay.AmenityMapper;
import clyvasync.Clyvasync.mapper.homestay.HomestayMapper;
import clyvasync.Clyvasync.modules.auth.entity.User;
import clyvasync.Clyvasync.modules.homestay.entity.*;
import clyvasync.Clyvasync.modules.room.RatePlanCalendar;
import clyvasync.Clyvasync.modules.room.RoomCalendar;
import clyvasync.Clyvasync.modules.room.RoomRatePlan;
import clyvasync.Clyvasync.modules.tour.entity.Tour;
import clyvasync.Clyvasync.modules.tour.entity.TourImage;
import clyvasync.Clyvasync.repository.homestay.*;
import clyvasync.Clyvasync.repository.room.RatePlanCalendarRepository;
import clyvasync.Clyvasync.repository.tour.TourRepository;
import clyvasync.Clyvasync.service.annotation.IsHomestayOwner;
import clyvasync.Clyvasync.service.auth.UserService;
import clyvasync.Clyvasync.service.booking.BookingDetailService;
import clyvasync.Clyvasync.service.booking.BookingService;
import clyvasync.Clyvasync.service.homestay.*;
import clyvasync.Clyvasync.service.room.RoomCalendarService;
import clyvasync.Clyvasync.service.room.RoomRatePlanService;
import clyvasync.Clyvasync.service.tour.TourImageService;
import clyvasync.Clyvasync.service.tour.TourService;
import clyvasync.Clyvasync.spec.HomestaySearchSpec;
import clyvasync.Clyvasync.spec.TourSearchSpec;
import clyvasync.Clyvasync.utils.MediaUtil;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class HomestayServiceImpl implements HomestayService {
    private final HomestayRepository homestayRepository;
    private final HomestayMapper homestayMapper;
    private final AmenityService amenityService;
    private final HomestayImageService homestayImageService;
    private final LocationService locationService;
    private final CategoryService categoryService;
    private final ReviewService reviewService;
    private final TourService tourService;
    private final UserService userService;
    private final HomestayRoomService homestayRoomService;
    private final FavoriteService favoriteService;
    private final TourImageService tourImageService;
    private final RoomCalendarService roomCalendarService;
    private final BookingDetailService bookingDetailService;
    private final BookingService bookingService;
    private final MediaUtil mediaUtil;
    private final RatePlanCalendarRepository ratePlanCalendarRepository;
    private final RoomRatePlanService roomRatePlanService;
    private final HomestayImageRepository homestayImageRepository;
    private final HomestaySearchIndexRepository homestaySearchIndexRepository;
    private final EmbeddingModel embeddingModel;
    private final HomestayRoomRepository homestayRoomRepository;
    private final clyvasync.Clyvasync.repository.booking.BookingRepository bookingRepository;

    public HomestayServiceImpl(HomestayRepository homestayRepository, HomestayMapper homestayMapper, AmenityService amenityService, HomestayImageService homestayImageService, LocationService locationService, CategoryService categoryService, ReviewService reviewService, TourService tourService, UserService userService, HomestayRoomService homestayRoomService, FavoriteService favoriteService, TourImageService tourImageService, RoomCalendarService roomCalendarService, BookingDetailService bookingDetailService, @Lazy BookingService bookingService, MediaUtil mediaUtil, RatePlanCalendarRepository ratePlanCalendarRepository, RoomRatePlanService roomRatePlanService, HomestayImageRepository homestayImageRepository, HomestaySearchIndexRepository homestaySearchIndexRepository,EmbeddingModel embeddingModel,HomestayRoomRepository homestayRoomRepository, clyvasync.Clyvasync.repository.booking.BookingRepository bookingRepository) {
        this.homestayRepository = homestayRepository;
        this.homestayMapper = homestayMapper;
        this.amenityService = amenityService;
        this.homestayImageService = homestayImageService;
        this.locationService = locationService;
        this.categoryService = categoryService;
        this.reviewService = reviewService;
        this.tourService = tourService;
        this.userService = userService;
        this.homestayRoomService = homestayRoomService;
        this.favoriteService = favoriteService;
        this.tourImageService = tourImageService;
        this.roomCalendarService = roomCalendarService;
        this.bookingDetailService = bookingDetailService;
        this.bookingService = bookingService;
        this.mediaUtil = mediaUtil;
        this.ratePlanCalendarRepository = ratePlanCalendarRepository;
        this.roomRatePlanService = roomRatePlanService;
        this.homestayImageRepository = homestayImageRepository;
        this.homestaySearchIndexRepository = homestaySearchIndexRepository;
        this.embeddingModel = embeddingModel;
        this.homestayRoomRepository = homestayRoomRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HomestayResponse createHomestay(HomestayRequest request, Long ownerId) {
        Homestay homestay = homestayMapper.toEntity(request);
        homestay.setOwnerId(ownerId);
        homestay.setStatus(HomestayStatus.DRAFT);
        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            Optional<Integer> locationIdOpt = locationService.findIdByNameOrSlug(request.getCity().trim());

            locationIdOpt.ifPresent(homestay::setLocationId);
        }
        Homestay savedHomestay = homestayRepository.save(homestay);

        // 2. MAPPING ẢNH (Từ PENDING sang ACTIVE)
        if (request.getObjectKeys() != null && !request.getObjectKeys().isEmpty()) {
            List<HomestayImage> imagesToMap = homestayImageService.findByImageUrlIn(request.getObjectKeys());

            for (HomestayImage img : imagesToMap) {
                img.setHomestayId(savedHomestay.getId());
                img.setStatus(MediaStatus.ACTIVE);
            }

            homestayImageService.saveAll(imagesToMap);
        }

        return homestayMapper.toResponse(savedHomestay);
    }


    @Override
    @IsHomestayOwner
    @Transactional
    public HomestayResponse updateHomestay(Long id, HomestayRequest request, Long ownerId) {
        // 1. Fetch entity
        Homestay homestay = homestayRepository.findById(id)
                .orElseThrow(() -> new AppException(ResultCode.HOMESTAY_NOT_FOUND));

        // 2. Update fields efficiently
        updateHomestayFields(homestay, request);

        // 3. Save once
        Homestay updatedHomestay = homestayRepository.save(homestay);

        // 4. Handle images
            processHomestayImages(updatedHomestay, request);


        this.homestayImageService.evictHomestayImagesCache(id);

        // 5. Build response with batch queries
        return buildHomestayResponse(updatedHomestay);
    }

    private void updateHomestayFields(Homestay homestay, HomestayRequest request) {
        ofNullable(request.getName()).filter(StringUtils::hasText).ifPresent(homestay::setName);
        ofNullable(request.getDescription()).filter(StringUtils::hasText).ifPresent(homestay::setDescription);
        ofNullable(request.getAddressDetail()).filter(StringUtils::hasText).ifPresent(homestay::setAddressDetail);
        ofNullable(request.getLatitude()).ifPresent(homestay::setLatitude);
        ofNullable(request.getLongitude()).ifPresent(homestay::setLongitude);
        ofNullable(request.getCategoryId()).ifPresent(homestay::setCategoryId);

        if (StringUtils.hasText(request.getCity())) {
            locationService.findIdByNameOrSlug(request.getCity().trim())
                    .ifPresent(homestay::setLocationId);
        }
    }

    private void processHomestayImages(Homestay homestay, HomestayRequest request) {
        List<ImageSubmitRequest> imageReqs = request.getImages();
        Long homestayId = homestay.getId();

        List<HomestayImage> existingImages = homestayImageRepository.findByHomestayId(homestayId);

        // 2. Xác định các ID cần xóa
        Set<Long> keepIds = imageReqs.stream()
                .map(ImageSubmitRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. Xóa những ảnh ACTIVE không nằm trong danh sách keepIds
        // Thay vì chạy câu lệnh delete trực tiếp, hãy để JPA quản lý danh sách object
        List<HomestayImage> toDelete = existingImages.stream()
                .filter(img -> MediaStatus.ACTIVE.equals(img.getStatus()) && !keepIds.contains(img.getId()))
                .toList();

        if (!toDelete.isEmpty()) {
            homestayImageRepository.deleteAll(toDelete);
            // Quan trọng: Xóa khỏi list đang làm việc để không bị merge nhầm
            existingImages.removeAll(toDelete);
        }

        // 4. Update các ảnh còn giữ lại (existingImages hiện tại đã sạch)
        existingImages.forEach(img -> {
            imageReqs.stream().filter(r -> r.getId() != null && r.getId().equals(img.getId()))
                    .findFirst().ifPresent(r -> {
                        img.setDisplayOrder(r.getSortOrder());
                        img.setIsPrimary(r.getIsCover());
                    });
        });
        homestayImageRepository.saveAll(existingImages);

        // 5. Active các ảnh mới (PENDING -> ACTIVE)
        List<String> newKeys = imageReqs.stream()
                .map(ImageSubmitRequest::getObjectKey)
                .filter(Objects::nonNull)
                .toList();

        if (!newKeys.isEmpty()) {
            List<HomestayImage> pendingImages = homestayImageRepository.findByImageUrlIn(newKeys);
            pendingImages.forEach(img -> {
                ImageSubmitRequest req = imageReqs.stream()
                        .filter(r -> r.getObjectKey() != null && r.getObjectKey().equals(img.getImageUrl()))
                        .findFirst().orElse(null);
                if (req != null) {
                    img.setHomestayId(homestayId);
                    img.setStatus(MediaStatus.ACTIVE);
                    img.setDisplayOrder(req.getSortOrder());
                    img.setIsPrimary(req.getIsCover());
                }
            });
            homestayImageRepository.saveAll(pendingImages);
        }
    }

    private HomestayResponse buildHomestayResponse(Homestay homestay) {
        HomestayResponse response = homestayMapper.toResponse(homestay);

        long homestayId = homestay.getId();

        response.setImageUrls(mediaUtil.toCdnUrls(
                homestayImageService.getImagesByHomestayId(homestayId)));
        response.setAmenities(amenityService.getAmenitiesByHomestayId(homestayId));

        loadLocationAndCategory(response, homestay);

        response.setOwner(userService.getOwnerInfo(homestay.getOwnerId()));

        loadRoomSummaries(response, homestayId);

        response.setAverageRating(
                BigDecimal.valueOf(
                        ofNullable(homestay.getAverageRating())
                                .map(BigDecimal::doubleValue)
                                .orElse(0.0)
                )
        );

        return response;
    }

    private void loadLocationAndCategory(HomestayResponse response, Homestay homestay) {
        if (homestay.getLocationId() != null) {
            Map<Integer, String> locationsMap = locationService.getLocationNamesMap(
                    List.of(homestay.getLocationId()));
            response.setCityName(locationsMap.get(homestay.getLocationId()));
        }

        if (homestay.getCategoryId() != null) {
            Map<Integer, String> categoriesMap = categoryService.getCategoryNamesMap(
                    List.of(homestay.getCategoryId()));
            response.setCategoryName(categoriesMap.get(homestay.getCategoryId()));
        }
    }

    private void loadRoomSummaries(HomestayResponse response, Long homestayId) {
        homestayRoomService.getRoomSummaries(List.of(homestayId))
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        summary -> {
                            response.setBasePrice(summary.getMinPrice());
                            response.setMaxGuests(summary.getMaxGuestsInRoom());
                            response.setNumBedrooms(summary.getTotalRooms());
                            response.setNumBathrooms(summary.getTotalRooms());
                        },
                        () -> {
                            response.setBasePrice(BigDecimal.ZERO);
                            response.setMaxGuests(0);
                            response.setNumBedrooms(0);
                            response.setNumBathrooms(0);
                        }
                );
    }
    @Override
    @IsHomestayOwner
    public void deleteHomestay(Long id, Long ownerId) {

    }

    @Override
    public HomestayResponse getById(Long id) {
        // 1. Lấy thực thể Homestay gốc
        Homestay homestay = homestayRepository.findById(id)
                .orElseThrow(() -> new AppException(ResultCode.HOMESTAY_NOT_FOUND));

        // 2. Map các trường cơ bản từ Entity sang Response
        HomestayResponse response = homestayMapper.toResponse(homestay);

        // 3. Lắp ráp dữ liệu Hình ảnh và Tiện ích
        response.setImageUrls(mediaUtil.toCdnUrls(homestayImageService.getImagesByHomestayId(id))); // Cần chắc chắn hàm này có tồn tại trong Service
        response.setAmenities(amenityService.getAmenitiesByHomestayId(id));

        // 4. Lắp ráp tên Thành phố và Danh mục
        Map<Integer, String> locationsMap = locationService.getLocationNamesMap(List.of(homestay.getLocationId()));
        response.setCityName(locationsMap.get(homestay.getLocationId()));

        Map<Integer, String> categoriesMap = categoryService.getCategoryNamesMap(List.of(homestay.getCategoryId()));
        response.setCategoryName(categoriesMap.get(homestay.getCategoryId()));

        // 5. Lắp ráp thông tin Chủ nhà (Owner)
        response.setOwner(userService.getOwnerInfo(homestay.getOwnerId()));

        // 6. Lắp ráp thông tin quy mô Phòng (Giá thấp nhất, Số khách, Số phòng)
        List<HomestayRoomSummary> summaries = homestayRoomService.getRoomSummaries(List.of(id));
        if (summaries != null && !summaries.isEmpty()) {
            HomestayRoomSummary summary = summaries.get(0);
            response.setBasePrice(summary.getMinPrice());
            response.setMaxGuests(summary.getMaxGuestsInRoom());
            response.setNumBedrooms(summary.getTotalRooms());
            response.setNumBathrooms(summary.getTotalRooms()); // Tạm dùng chung theo logic cũ của bạn
        } else {
            response.setBasePrice(BigDecimal.ZERO);
            response.setMaxGuests(0);
            response.setNumBedrooms(0);
            response.setNumBathrooms(0);
        }

        // 7. Xử lý Rating (Đảm bảo không bị null)
        response.setAverageRating(BigDecimal.valueOf(homestay.getAverageRating() != null ? homestay.getAverageRating().doubleValue() : 0.0));

        return response;
    }

    @Override
    public Page<HomestayResponse> searchHomestays(HomestaySearchRequest filters, Pageable pageable) {
        log.info("[SEARCH V2] Searching homestays with cinematic filters: {}", filters);

        Specification<Homestay> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. TÌM KIẾM TỪ KHÓA (Gần đúng, không phân biệt hoa thường)
            if (StringUtils.hasText(filters.city())) {
                // Mẹo Postgres: Nếu DB bác có cài EXTENSION unaccent, bác có thể đổi cb.lower thành:
                // cb.lower(cb.function("unaccent", String.class, ...)) để tìm tiếng Việt không dấu siêu chuẩn.
                String searchPattern = "%" + filters.city().trim().toLowerCase() + "%";

                // 1.1 Tìm trong tên Thành phố (Location Subquery)
                Subquery<Integer> locationSubquery = query.subquery(Integer.class);
                Root<Location> locationRoot = locationSubquery.from(Location.class);
                locationSubquery.select(locationRoot.get("id"));
                Predicate cityMatch = cb.or(
                        cb.like(cb.lower(locationRoot.get("cityName")), searchPattern),
                        cb.equal(locationRoot.get("slug"), filters.city())
                );
                locationSubquery.where(cityMatch);
                Predicate matchLocation = root.get("locationId").in(locationSubquery);

                // 1.2 Tìm trực tiếp trong tên Homestay
                Predicate matchName = cb.like(
                        cb.lower(cb.function("unaccent", String.class, root.get("name"))),
                        cb.function("unaccent", String.class, cb.literal(searchPattern))
                );

                // Gộp lại: Có trong Tên HOẶC Có trong Thành phố đều lấy
                predicates.add(cb.or(matchName, matchLocation));
            }

            // 2. LỌC THEO GIÁ (Ngân sách)
            if (filters.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), filters.minPrice()));
            }
            if (filters.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), filters.maxPrice()));
            }

            // 3. QUY MÔ (Số khách & Phòng ngủ) - Chỉ lọc nếu > 0
            if (filters.guests() != null && filters.guests() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxGuests"), filters.guests()));
            }
            if (filters.bedrooms() != null && filters.bedrooms() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("numBedrooms"), filters.bedrooms()));
            }

            // 4. LỌC THEO SỐ SAO (Rating)
            if (filters.minRating() != null && filters.minRating() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("averageRating"), filters.minRating()));
            }

            // 5. LỌC CATEGORY (Tour hay Homestay)
            if (filters.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filters.categoryId()));
            }

            // 6. LỌC TIỆN ÍCH (Yêu cầu phải có TẤT CẢ các tiện ích được chọn)
            if (filters.amenityIds() != null && !filters.amenityIds().isEmpty()) {
                // Tùy vào cách cấu hình Entity của bác:
                // CÁCH 1 (Dễ nhất): Nếu trong entity Homestay bác có @ManyToMany List<Amenity> amenities
            /*
            for (Integer amId : filters.amenityIds()) {
                predicates.add(cb.isMember(amId, root.get("amenities"))); // Hibernate tự JOIN bảng phụ
            }
            */

                // CÁCH 2: Nếu bác lưu Array List Integer thẳng vào PostgreSQL (Cột JSONB hoặc INT[])
            for (Integer amId : filters.amenityIds()) {
                predicates.add(cb.isTrue(cb.function("jsonb_contains", Boolean.class, root.get("amenityIds"), cb.literal(amId.toString()))));
            }


                // Note: Bác mở comment cách nào phù hợp với kiến trúc Entity của bác nhé!
            }

            // 7. FIX CỨNG: Trạng thái hiển thị
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), HomestayStatus.APPROVED));

            // Nếu query.where() chưa được gọi, JPA tự hiểu là lấy danh sách các Predicate này nối với nhau bằng AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // THỰC THI QUERY ĐỘNG TỚI DB
        Page<Homestay> homestayPage = homestayRepository.findAll(spec, pageable);
        List<Homestay> homestays = homestayPage.getContent();

        if (homestays.isEmpty()) {
            return Page.empty(pageable); // Thoát sớm nếu không tìm thấy, tránh chạy code thừa
        }

        // =========================================================================
        // ĐOẠN DƯỚI NÀY LÀ TUYỆT KỸ BÁC VŨ ĐÃ VIẾT ĐỂ CHỐNG N+1 QUERY (Giữ nguyên)
        // =========================================================================
        List<Long> ids = homestays.stream().map(Homestay::getId).toList();
        List<Integer> locationIds = homestays.stream().map(Homestay::getLocationId).distinct().toList();
        List<Integer> categoryIds = homestays.stream().map(Homestay::getCategoryId).distinct().toList();

        List<HomestayRoomSummary> summaries = homestayRoomService.getRoomSummaries(ids);
        Map<Long, HomestayRoomSummary> roomSummaryMap = summaries.stream()
                .collect(Collectors.toMap(HomestayRoomSummary::getHomestayId, s -> s));

        Map<Long, List<AmenityResponse>> amenitiesMap = amenityService.getAmenitiesForHomestays(ids);
        Map<Long, List<String>> imagesMap = homestayImageService.getImagesForHomestays(ids);
        Map<Integer, String> locationsMap = locationService.getLocationNamesMap(locationIds);
        Map<Integer, String> categoriesMap = categoryService.getCategoryNamesMap(categoryIds);

        // MAPPING SANG DTO
        return homestayPage.map(entity -> {
            HomestayResponse response = homestayMapper.toResponse(entity);
            response.setImageUrls(mediaUtil.toCdnUrls(imagesMap.getOrDefault(entity.getId(), List.of())));
            response.setCityName(locationsMap.get(entity.getLocationId()));
            response.setCategoryName(categoriesMap.get(entity.getCategoryId()));
            response.setAmenities(amenitiesMap.getOrDefault(entity.getId(), List.of()));

            response.setAverageRating(BigDecimal.valueOf(entity.getAverageRating() != null ? entity.getAverageRating().doubleValue() : 0.0));

            HomestayRoomSummary summary = roomSummaryMap.get(entity.getId());
            if (summary != null) {
                response.setBasePrice(summary.getMinPrice());
                response.setMaxGuests(summary.getMaxGuestsInRoom());
                response.setNumBedrooms(summary.getTotalRooms());
                response.setNumBathrooms(summary.getTotalRooms()); // Tạm dùng chung theo logic cũ của bác
            } else {
                response.setBasePrice(BigDecimal.ZERO);
                response.setMaxGuests(0);
                response.setNumBedrooms(0);
            }
            return response;
        });
    }

    @Override
    public List<HomestayResponse> getByOwnerId(Long ownerId) {
        // 1. Lấy danh sách Homestay của đúng Host đó
        List<Homestay> homestays = homestayRepository.findAllByOwnerId(ownerId);
        List<Long> ids = homestays.stream().map(Homestay::getId).toList();
        List<Integer> locationIds = homestays.stream().map(Homestay::getLocationId).distinct().toList();
        List<Integer> categoryIds = homestays.stream().map(Homestay::getCategoryId).distinct().toList();

        List<HomestayRoomSummary> summaries = homestayRoomService.getRoomSummaries(ids);
        Map<Long, HomestayRoomSummary> roomSummaryMap = summaries.stream()
                .collect(Collectors.toMap(HomestayRoomSummary::getHomestayId, s -> s));

        Map<Long, List<AmenityResponse>> amenitiesMap = amenityService.getAmenitiesForHomestays(ids);
        Map<Long, List<String>> imagesMap = homestayImageService.getImagesForHomestays(ids);
        Map<Integer, String> locationsMap = locationService.getLocationNamesMap(locationIds);
        Map<Integer, String> categoriesMap = categoryService.getCategoryNamesMap(categoryIds);

        // MAPPING SANG DTO
        return homestays.stream().map(entity -> {
            HomestayResponse response = homestayMapper.toResponse(entity);
            response.setImageUrls(mediaUtil.toCdnUrls(imagesMap.getOrDefault(entity.getId(), List.of())));
            response.setCityName(locationsMap.get(entity.getLocationId()));
            response.setCategoryName(categoriesMap.get(entity.getCategoryId()));
            response.setAmenities(amenitiesMap.getOrDefault(entity.getId(), List.of()));

            response.setAverageRating(BigDecimal.valueOf(entity.getAverageRating() != null ? entity.getAverageRating().doubleValue() : 0.0));

            HomestayRoomSummary summary = roomSummaryMap.get(entity.getId());
            if (summary != null) {
                response.setBasePrice(summary.getMinPrice());
                response.setMaxGuests(summary.getMaxGuestsInRoom());
                response.setNumBedrooms(summary.getTotalRooms());
                response.setNumBathrooms(summary.getTotalRooms());
            } else {
                response.setBasePrice(BigDecimal.ZERO);
                response.setMaxGuests(0);
                response.setNumBedrooms(0);
            }
            return response;
        }).toList();


    }

    @Override
    public List<Homestay> findByOwnerId(Long ownerId) {
        return homestayRepository.findAllByOwnerId(ownerId);
    }

    @Override
    @IsHomestayOwner
    public void updateStatus(Long id, String status, Long ownerId) {

    }

    @Override
    @IsHomestayOwner
    public void updateAverageRating(Long id, BigDecimal newRating) {

    }

    @Override
    public HomestayDetailResponse getHomestayDetail(Long currentUserId, Long id, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        log.info("Getting homestay detail for user {} with ID {}", currentUserId, id);

        Homestay homestay = homestayRepository.findById(id)
                .orElseThrow(() -> new AppException(ResultCode.HOMESTAY_NOT_FOUND));
        if(homestay.getStatus() != HomestayStatus.APPROVED) {
            return new HomestayDetailResponse();
        }

        List<HomestayImage> homestayImages =
                homestayImageRepository.findByHomestayId(homestay.getId());

        List<HomestayImageResponse> images = homestayImages.stream()
                .filter(img -> MediaStatus.ACTIVE.equals(img.getStatus()))
                .sorted(Comparator.comparing(
                        HomestayImage::getDisplayOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(img -> HomestayImageResponse.builder()
                        .id(img.getId())
                        .objectKey(img.getImageUrl())
                        .imageUrl(mediaUtil.toCdnUrl(img.getImageUrl()))
                        .isCover(img.getIsPrimary())
                        .displayOrder(img.getDisplayOrder())
                        .build())
                .toList();


        List<AmenityResponse> amenities = amenityService.getAmenitiesByHomestayId(id);

        String cityName = null;
        if (homestay.getLocationId() != null) {
            cityName = locationService.getLocationNamesMap(List.of(homestay.getLocationId()))
                    .get(homestay.getLocationId());
        }

        String categoryName = null;
        if (homestay.getCategoryId() != null) {
            categoryName = categoryService.getCategoryNamesMap(List.of(homestay.getCategoryId()))
                    .get(homestay.getCategoryId());
        }

        List<ReviewResponse> reviews = reviewService.getReviewsByHomestayId(id);
        List<RoomResponse> rooms;
        if (checkIn != null && checkOut != null) {
            int guestCount = (guests != null) ? guests : 1;
            rooms = homestayRoomService.findAvailableRooms(id, checkIn, checkOut, guestCount);
        } else {
            rooms = homestayRoomService.getAllRoomsByHomestay(id);
        }


        return HomestayDetailResponse.builder()
                .id(homestay.getId())
                .name(homestay.getName())
                .description(homestay.getDescription())
                .addressDetail(homestay.getAddressDetail())
                .latitude(homestay.getLatitude() != null ? homestay.getLatitude().doubleValue() : null)
                .longitude(homestay.getLongitude() != null ? homestay.getLongitude().doubleValue() : null)
                .status(homestay.getStatus())
                .averageRating(homestay.getAverageRating())
                .reviewCount(homestay.getReviewCount())
                .cityName(cityName)
                .categoryName(categoryName)
                .images(images)
                .amenities(amenities)
                .isFavorite(favoriteService.existsHomestayFavoriteByHomestayId(homestay.getId()))
                .owner(userService.getOwnerInfo(homestay.getOwnerId()))
                .reviews(reviews)
                .tours(tourService.getAvailableToursForBookingDates(homestay.getId(), checkIn, checkOut))
                .rooms(rooms)
                .build();


    }

    @Override
    public List<Homestay> findByIdIn(List<Long> ids) {
        return  homestayRepository.findByIdIn(ids);
    }

    @Override
    public List<GlobalSearchResponse> cinematicSearch(GlobalSearchRequest request) {
        log.info("[GLOBAL SEARCH] Triggered with keyword: {}", request.keyword());
        List<GlobalSearchResponse> unifiedResults = new ArrayList<>();
        String category = request.category() != null ? request.category().toUpperCase() : "ALL";

        // 1. TÌM VÀ MAP HOMESTAY
        if (category.equals("ALL") || category.equals("HOMESTAY")) {
            List<Homestay> homestays = homestayRepository.findAll(HomestaySearchSpec.buildGlobalSpec(request), PageRequest.of(0, 20)).getContent();
            System.out.println(homestays.size());
            unifiedResults.addAll(mapHomestaysToResponse(homestays));
        }

        // 2. TÌM VÀ MAP TOUR
        if (category.equals("ALL") || category.equals("TOUR")) {
            List<Tour> tours = tourService.findAll(TourSearchSpec.buildGlobalSpec(request), PageRequest.of(0, 20)).getContent();
            unifiedResults.addAll(mapToursToResponse(tours));
        }

        // 3. MIX & SORT: Ưu tiên Rating cao nhất
        unifiedResults.sort(Comparator.comparing(GlobalSearchResponse::rating, Comparator.nullsLast(Comparator.reverseOrder())));

        return unifiedResults;
    }

    @Override
    public Homestay findById(Long id) {
        return homestayRepository.findById(id).orElseThrow(() -> new AppException(ResultCode.HOMESTAY_NOT_FOUND));
    }

    @Override
    @IsHomestayOwner
    public HomestayTimelineResponse getHomestayTimeline(Long homestayId, LocalDate startDate, LocalDate endDate) {

        List<HomestayRoom> rooms = homestayRoomService.findAllById(homestayId);
        List<Long> roomIds = rooms.stream().map(HomestayRoom::getId).toList();

        if (roomIds.isEmpty()) {
            return new HomestayTimelineResponse(homestayId, startDate, endDate, List.of());
        }

        // ==========================================
        // 1. BULK FETCH DỮ LIỆU TỒN PHÒNG & BOOKING
        // ==========================================
        List<RoomCalendar> calendars = roomCalendarService.findCalendarsByRoomIdsAndDateRange(roomIds, startDate, endDate);
        List<BookingTimelineProjection> bookings = bookingDetailService.findOverlappingBookings(roomIds, startDate, endDate);

        // 👉 KÉO THÊM DỮ LIỆU GÓI GIÁ
        List<RoomRatePlan> allRatePlans = roomRatePlanService.getAllRoomRatePlans(roomIds);
        List<Long> ratePlanIds = allRatePlans.stream().map(RoomRatePlan::getId).toList();
        List<RatePlanCalendar> rateCalendars = ratePlanCalendarRepository.findByRatePlanIdInAndNightDateBetween(ratePlanIds, startDate, endDate);

        // ==========================================
        // 2. MEMORY GROUPING (Map dữ liệu)
        // ==========================================
        Map<Long, Map<LocalDate, RoomCalendar>> calendarMap = calendars.stream()
                .collect(Collectors.groupingBy(RoomCalendar::getRoomId,
                        Collectors.toMap(RoomCalendar::getNightDate, c -> c)));

        Map<Long, List<BookingTimelineProjection>> bookingMap = bookings.stream()
                .collect(Collectors.groupingBy(BookingTimelineProjection::roomId));

        Map<Long, List<RoomRatePlan>> ratePlanMap = allRatePlans.stream()
                .collect(Collectors.groupingBy(RoomRatePlan::getRoomId));

        Map<Long, Map<LocalDate, RatePlanCalendar>> rateCalendarMap = rateCalendars.stream()
                .collect(Collectors.groupingBy(RatePlanCalendar::getRatePlanId,
                        Collectors.toMap(RatePlanCalendar::getNightDate, r -> r)));

        // ==========================================
        // 3. LẮP RÁP KẾT QUẢ CHO TIMELINE
        // ==========================================
        List<RoomTimelineResponse> roomTimelines = rooms.stream().map(room -> {

            List<RoomRatePlan> roomPlans = ratePlanMap.getOrDefault(room.getId(), List.of());
            Map<LocalDate, RoomCalendar> roomCalendars = calendarMap.getOrDefault(room.getId(), Collections.emptyMap());

            // 👉 CHẠY VÒNG LẶP ĐỂ ĐẢM BẢO TIMELINE KHÔNG BỊ "THỦNG" NGÀY NÀO
            List<DailyStatusResponse> dailyStatuses = new ArrayList<>();
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                final LocalDate currentDate = date;
                RoomCalendar cal = roomCalendars.get(currentDate);

                // Thuật toán lấy giá rẻ nhất của ngày hôm đó
                BigDecimal minPrice = roomPlans.stream()
                        .map(rp -> {
                            Map<LocalDate, RatePlanCalendar> rpCalMap = rateCalendarMap.getOrDefault(rp.getId(), Collections.emptyMap());
                            // Nếu có giá đè ngày này -> Lấy giá đè. Không thì -> Lấy giá gốc
                            return rpCalMap.containsKey(currentDate) ? rpCalMap.get(currentDate).getPrice() : rp.getPrice();
                        })
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                // Số lượng tồn phòng
                int availableQty = (cal != null && cal.getAvailableQuantity() != null)
                        ? cal.getAvailableQuantity()
                        : room.getQuantity();

                dailyStatuses.add(DailyStatusResponse.builder()
                        .date(currentDate)
                        .price(minPrice)
                        .availableQuantity(availableQty)
                        .build());
            }

            List<BookingBlockResponse> bookingBlocks = bookingMap.getOrDefault(room.getId(), List.of())
                    .stream()
                    .map(b -> BookingBlockResponse.builder()
                            .bookingId(b.bookingId())
                            .guestName(b.guestName())
                            .checkInDate(b.checkInDate())
                            .checkOutDate(b.checkOutDate())
                            .status(b.status())
                            .build())
                    .toList();

            return RoomTimelineResponse.builder()
                    .roomId(room.getId())
                    .roomName(room.getName())
                    .dailyStatuses(dailyStatuses)
                    .bookings(bookingBlocks)
                    .build();

        }).toList();

        return new HomestayTimelineResponse(homestayId, startDate, endDate, roomTimelines);
    }

    @Override
    public Map<Long, HomestayTimelineResponse> getBatchTimeline(List<Long> homestayIds, LocalDate startDate, LocalDate endDate) {
        // ==========================================
        // 1. LẤY TẤT CẢ PHÒNG CỦA TẤT CẢ HOMESTAYS
        // ==========================================
        List<HomestayRoom> allRooms = homestayRoomService.findAllByIdIn(homestayIds);
        List<Long> allRoomIds = allRooms.stream().map(HomestayRoom::getId).toList();

        if (allRoomIds.isEmpty()) {
            return new HashMap<>(); // Trả về map rỗng nếu không có phòng nào
        }

        // ==========================================
        // 2. KÉO DỮ LIỆU TỒN PHÒNG & BOOKING
        // ==========================================
        List<RoomCalendar> allCalendars = roomCalendarService.findCalendarsByRoomIdsAndDateRange(allRoomIds, startDate, endDate);
        List<BookingTimelineProjection> allBookings = bookingDetailService.findOverlappingBookings(allRoomIds, startDate, endDate);

        // Lấy Avatar Users
        List<Long> userIds = allBookings.stream().map(BookingTimelineProjection::userId).toList();
        Map<Long, String> ownerResponseMap = userService.getImageUsers(userIds);

        // ==========================================
        // 3. KÉO DỮ LIỆU GÓI GIÁ & LỊCH GIÁ (MỚI)
        // ==========================================
        List<RoomRatePlan> allRatePlans = roomRatePlanService.getAllRoomRatePlans(allRoomIds);
        List<Long> ratePlanIds = allRatePlans.stream().map(RoomRatePlan::getId).toList();
        List<RatePlanCalendar> rateCalendars = ratePlanCalendarRepository.findByRatePlanIdInAndNightDateBetween(ratePlanIds, startDate, endDate);

        // Kéo Ảnh phòng
        Map<Long, String> roomImageMap = homestayRoomService.getRoomImageMap(allRoomIds);

        // ==========================================
        // 4. MEMORY GROUPING (GOM NHÓM DỮ LIỆU TRÊN RAM)
        // ==========================================
        Map<Long, List<HomestayRoom>> roomsByHomeMap = allRooms.stream()
                .collect(Collectors.groupingBy(HomestayRoom::getHomestayId));

        Map<Long, Map<LocalDate, RoomCalendar>> calendarMap = allCalendars.stream()
                .collect(Collectors.groupingBy(RoomCalendar::getRoomId,
                        Collectors.toMap(RoomCalendar::getNightDate, c -> c)));

        Map<Long, List<BookingTimelineProjection>> bookingMap = allBookings.stream()
                .collect(Collectors.groupingBy(BookingTimelineProjection::roomId));

        // Nhóm gói giá theo roomId
        Map<Long, List<RoomRatePlan>> ratePlanMap = allRatePlans.stream()
                .collect(Collectors.groupingBy(RoomRatePlan::getRoomId));

        // Nhóm Lịch giá theo ratePlanId -> Ngày
        Map<Long, Map<LocalDate, RatePlanCalendar>> rateCalendarMap = rateCalendars.stream()
                .collect(Collectors.groupingBy(RatePlanCalendar::getRatePlanId,
                        Collectors.toMap(RatePlanCalendar::getNightDate, r -> r)));

        // ==========================================
        // 5. LẮP RÁP KẾT QUẢ CHUẨN XÁC
        // ==========================================
        Map<Long, HomestayTimelineResponse> result = new HashMap<>();

        for (Long homeId : homestayIds) {
            // Lấy danh sách phòng của Homestay đang chạy
            List<HomestayRoom> homeRooms = roomsByHomeMap.getOrDefault(homeId, Collections.emptyList());

            List<RoomTimelineResponse> roomTimelines = homeRooms.stream().map(room -> {

                // Bốc dữ liệu RIÊNG cho phòng này từ các kho tổng
                Map<LocalDate, RoomCalendar> roomCals = calendarMap.getOrDefault(room.getId(), Collections.emptyMap());
                List<RoomRatePlan> roomPlans = ratePlanMap.getOrDefault(room.getId(), Collections.emptyList());
                List<BookingTimelineProjection> roomBookings = bookingMap.getOrDefault(room.getId(), Collections.emptyList());
                String roomImage = roomImageMap.get(room.getId());

                // Truyền vào helper mapToRoomTimeline đã được nâng cấp
                return mapToRoomTimeline(
                        room,
                        startDate,
                        endDate,
                        roomCals,
                        roomPlans,
                        rateCalendarMap,
                        roomBookings,
                        roomImage,
                        ownerResponseMap
                );

            }).toList();

            result.put(homeId, new HomestayTimelineResponse(homeId, startDate, endDate, roomTimelines));
        }

        return result;
    }

    @Override
    public PortfolioTimelineResponse getOwnerPortfolioTimeline(Long ownerId, int month, int year) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // ==========================================
        // 1 & 2. LẤY HOMESTAY VÀ ROOMS
        // ==========================================
        List<Homestay> allHomes = homestayRepository.findAllByOwnerId(ownerId);
        List<Long> homeIds = allHomes.stream().map(Homestay::getId).toList();

        if (homeIds.isEmpty()) return new PortfolioTimelineResponse(List.of());

        List<HomestayRoom> allRooms = homestayRoomService.findAllByIdIn(homeIds);
        List<Long> allRoomIds = allRooms.stream().map(HomestayRoom::getId).toList();
        Map<Long, String> roomImageMap = homestayRoomService.getRoomImageMap(allRoomIds);

        // ==========================================
        // 3. QUERY LỊCH, BOOKING VÀ GÓI GIÁ
        // ==========================================
        List<RoomCalendar> allCalendars = roomCalendarService.findCalendarsByRoomIdsAndDateRange(allRoomIds, startDate, endDate);
        List<BookingTimelineProjection> allBookings = bookingDetailService.findOverlappingBookings(allRoomIds, startDate, endDate);

        // 👉 Kéo dữ liệu Gói giá và Lịch giá (BỔ SUNG)
        List<RoomRatePlan> allRatePlans = roomRatePlanService.getAllRoomRatePlans(allRoomIds);
        List<Long> ratePlanIds = allRatePlans.stream().map(RoomRatePlan::getId).toList();
        List<RatePlanCalendar> rateCalendars = ratePlanCalendarRepository.findByRatePlanIdInAndNightDateBetween(ratePlanIds, startDate, endDate);

        // ==========================================
        // 4. GOM NHÓM DỮ LIỆU VÀO MAP TRÊN RAM
        // ==========================================

        // Đã chuyển thành Map 2 lớp: RoomId -> (NightDate -> RoomCalendar)
        Map<Long, Map<LocalDate, RoomCalendar>> calendarMap = allCalendars.stream()
                .collect(Collectors.groupingBy(RoomCalendar::getRoomId,
                        Collectors.toMap(RoomCalendar::getNightDate, c -> c)));

        Map<Long, List<BookingTimelineProjection>> bookingMap = allBookings.stream()
                .collect(Collectors.groupingBy(BookingTimelineProjection::roomId));

        Map<Long, List<HomestayRoom>> roomsByHomeMap = allRooms.stream()
                .collect(Collectors.groupingBy(HomestayRoom::getHomestayId));

        // Map Avatar
        List<Long> userIds = allBookings.stream().map(BookingTimelineProjection::userId).toList();
        Map<Long, String> ownerResponseMap = userService.getImageUsers(userIds);

        // 👉 Nhóm gói giá theo roomId
        Map<Long, List<RoomRatePlan>> ratePlanMap = allRatePlans.stream()
                .collect(Collectors.groupingBy(RoomRatePlan::getRoomId));

        // 👉 Nhóm Lịch giá: RatePlanId -> (NightDate -> RatePlanCalendar)
        Map<Long, Map<LocalDate, RatePlanCalendar>> rateCalendarMap = rateCalendars.stream()
                .collect(Collectors.groupingBy(RatePlanCalendar::getRatePlanId,
                        Collectors.toMap(RatePlanCalendar::getNightDate, r -> r)));

        // Map ảnh Homestay
        Map<Long, List<String>> homestayImageMap = homestayImageService.getImagesForHomestays(homeIds);

        // ==========================================
        // 5. MAP SANG CẤU TRÚC PORTFOLIO
        // ==========================================
        List<HomeTimelineResponse> homeTimelines = allHomes.stream().map(home -> {

            List<RoomTimelineResponse> roomResponses = roomsByHomeMap.getOrDefault(home.getId(), List.of()).stream()
                    .map(room -> {
                        // 👉 BỐC TÁCH DỮ LIỆU CỦA RIÊNG PHÒNG NÀY TỪ KHO TỔNG
                        Map<LocalDate, RoomCalendar> roomCals = calendarMap.getOrDefault(room.getId(), Collections.emptyMap());
                        List<RoomRatePlan> roomPlans = ratePlanMap.getOrDefault(room.getId(), Collections.emptyList());
                        List<BookingTimelineProjection> roomBookings = bookingMap.getOrDefault(room.getId(), Collections.emptyList());
                        String roomImage = roomImageMap.get(room.getId());

                        // 👉 GỌI HÀM HELPER VỚI ĐẦY ĐỦ THAM SỐ MỚI
                        return mapToRoomTimeline(
                                room,
                                startDate,
                                endDate,
                                roomCals,
                                roomPlans,
                                rateCalendarMap,
                                roomBookings,
                                roomImage,
                                ownerResponseMap
                        );
                    })
                    .toList();

            // Lấy ảnh đầu tiên làm ảnh đại diện
            List<String> images = mediaUtil.toCdnUrls(homestayImageMap.getOrDefault(home.getId(), List.of()));
            String primaryImage = !images.isEmpty() ? images.get(0) : null;

            return HomeTimelineResponse.builder()
                    .homeId(home.getId())
                    .homeName(home.getName())
                    .address(home.getAddressDetail())
                    .primaryImageUrl(primaryImage)
                    .rooms(roomResponses)
                    .build();

        }).toList();

        return new PortfolioTimelineResponse(homeTimelines);
    }

    @Override
    public List<PropertySummaryResponse> getHostProperties(Long hostId) {
        log.info("[PORTFOLIO] Fetching properties for host ID: {}", hostId);

        List<Homestay> homestays = homestayRepository.findAllByOwnerId(hostId);
        if (homestays.isEmpty()) {
            return List.of(); // Thoát sớm nếu Host chưa có tài sản nào
        }

        List<Long> homeIds = homestays.stream().map(Homestay::getId).toList();
        List<Integer> locationIds = homestays.stream().map(Homestay::getLocationId).distinct().toList();
        List<Integer> categoryIds = homestays.stream().map(Homestay::getCategoryId).distinct().toList();

        Map<Long, List<String>> imagesMap = homestayImageService.getImagesForHomestays(homeIds);
        Map<Integer, String> locationsMap = locationService.getLocationNamesMap(locationIds);
        Map<Integer, String> categoriesMap = categoryService.getCategoryNamesMap(categoryIds);

        List<HomestayRoomSummary> summaries = homestayRoomService.getRoomSummaries(homeIds);
        Map<Long, HomestayRoomSummary> roomSummaryMap = summaries.stream()
                .collect(Collectors.toMap(HomestayRoomSummary::getHomestayId, s -> s));

        List<HomestayRoom> allRooms = homestayRoomService.findAllByIdIn(homeIds);
        List<Long> allRoomIds = allRooms.stream().map(HomestayRoom::getId).toList();

        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        // Query lô (Batch): Lấy toàn bộ Booking của các phòng này trong tháng
        List<BookingTimelineProjection> allBookingsInMonth = new ArrayList<>();
        if (!allRoomIds.isEmpty()) { // Bảo vệ lỗi SQL IN (empty list)
            allBookingsInMonth = bookingDetailService.findOverlappingBookings(allRoomIds, startOfMonth, endOfMonth);
        }

        Map<Long, Integer> occupancyMap = calculateBatchOccupancy(homeIds, allRooms, allBookingsInMonth, startOfMonth, endOfMonth);

        return homestays.stream().map(home -> {
            HomestayRoomSummary roomSummary = roomSummaryMap.get(home.getId());
            BigDecimal basePrice = (roomSummary != null && roomSummary.getMinPrice() != null)
                    ? roomSummary.getMinPrice()
                    : BigDecimal.ZERO;

            List<String> images = imagesMap.getOrDefault(home.getId(), List.of());
            String coverImage = images.isEmpty() ? null : images.get(0);

            // Thống kê: Rating và Reviews
            Double rating = home.getAverageRating() != null ? home.getAverageRating().doubleValue() : 0.0;
            Integer reviews = home.getReviewCount() != null ? home.getReviewCount() : 0;

            Integer occupancy = occupancyMap.getOrDefault(home.getId(), 0);

            return PropertySummaryResponse.builder()
                    .id(home.getId())
                    .name(home.getName())
                    .type(categoriesMap.getOrDefault(home.getCategoryId(), "Homestay"))
                    .location(locationsMap.getOrDefault(home.getLocationId(), "Chưa cập nhật"))
                    .image(mediaUtil.toCdnUrl(coverImage))
                    .price(basePrice)
                    .status(home.getStatus() != null ? home.getStatus() : HomestayStatus.APPROVED)
                    .stats(PropertyStats.builder()
                            .rating(rating)
                            .reviews(reviews)
                            .occupancy(occupancy)
                            .build())
                    .build();
        }).toList();
    }

    @Override
    public HostPortfolioSummaryResponse getPortfolioSummary(Long hostId) {
        log.info("[PORTFOLIO] Calculating REAL summary report for host ID: {}", hostId);

        List<Homestay> homestays = homestayRepository.findAllByOwnerId(hostId);
        int totalProperties = homestays.size();

        if (totalProperties == 0) {
            return HostPortfolioSummaryResponse.builder()
                    .totalPortfolioValue(BigDecimal.ZERO).portfolioGrowthRate(0.0)
                    .averageOccupancyRate(0.0).occupancyTrend("N/A")
                    .averageRating(0.0).ratingGrowth(0.0)
                    .totalProperties(0).build();
        }

        List<Long> homeIds = homestays.stream().map(Homestay::getId).toList();
        List<HomestayRoom> allRooms = homestayRoomService.findAllByIdIn(homeIds);
        List<Long> allRoomIds = allRooms.stream().map(HomestayRoom::getId).toList();

        // ==========================================
        // 1. TÍNH TOÁN DOANH THU & TĂNG TRƯỞNG (REVENUE)
        // ==========================================
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        OffsetDateTime endOfThisMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59);

        OffsetDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);
        OffsetDateTime endOfLastMonth = startOfThisMonth.minusSeconds(1);

        BigDecimal currentRevenue = bookingService.sumRevenueByHomestaysAndDateRange(homeIds, startOfThisMonth, endOfThisMonth);
        BigDecimal lastMonthRevenue = bookingService.sumRevenueByHomestaysAndDateRange(homeIds, startOfLastMonth, endOfLastMonth);

        double portfolioGrowthRate = 0.0;
        if (lastMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
            // Công thức: ((Tháng này - Tháng trước) / Tháng trước) * 100
            BigDecimal growth = currentRevenue.subtract(lastMonthRevenue)
                    .divide(lastMonthRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            portfolioGrowthRate = growth.setScale(1, RoundingMode.HALF_UP).doubleValue();
        } else if (currentRevenue.compareTo(BigDecimal.ZERO) > 0) {
            portfolioGrowthRate = 100.0; // Từ 0 lên có doanh thu tính là tăng 100%
        }

        // ==========================================
        // 2. TÍNH TOÁN LẤP ĐẦY & XU HƯỚNG (OCCUPANCY)
        // ==========================================
        YearMonth currentYearMonth = YearMonth.now();
        YearMonth lastYearMonth = currentYearMonth.minusMonths(1);

        List<BookingTimelineProjection> currentBookings = new ArrayList<>();
        List<BookingTimelineProjection> lastMonthBookings = new ArrayList<>();

        if (!allRoomIds.isEmpty()) {
            currentBookings = bookingDetailService.findOverlappingBookings(allRoomIds, currentYearMonth.atDay(1), currentYearMonth.atEndOfMonth());
            lastMonthBookings = bookingDetailService.findOverlappingBookings(allRoomIds, lastYearMonth.atDay(1), lastYearMonth.atEndOfMonth());
        }

        // Chạy thuật toán Batch 2 lần cho 2 tháng
        Map<Long, Integer> currentOccupancyMap = calculateBatchOccupancy(homeIds, allRooms, currentBookings, currentYearMonth.atDay(1), currentYearMonth.atEndOfMonth());
        Map<Long, Integer> lastMonthOccupancyMap = calculateBatchOccupancy(homeIds, allRooms, lastMonthBookings, lastYearMonth.atDay(1), lastYearMonth.atEndOfMonth());

        double avgOccupancy = currentOccupancyMap.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double prevAvgOccupancy = lastMonthOccupancyMap.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

        // Xác định xu hướng (Chênh lệch > 2% mới tính là tăng/giảm)
        String occupancyTrend = "Ổn định";
        if (avgOccupancy > prevAvgOccupancy + 2.0) occupancyTrend = "Tăng trưởng";
        else if (avgOccupancy < prevAvgOccupancy - 2.0) occupancyTrend = "Giảm nhẹ";

        // ==========================================
        // 3. TÍNH TOÁN RATING & TĂNG TRƯỞNG (RATING)
        // ==========================================
        // Lấy điểm trung bình tích luỹ tới cuối tháng này
        Double currentAvgRating = reviewService.getAverageRatingByHomestaysUpToDate(homeIds, endOfThisMonth);
        // Lấy điểm trung bình tích luỹ tới cuối tháng trước
        Double prevAvgRating = reviewService.getAverageRatingByHomestaysUpToDate(homeIds, endOfLastMonth);

        // Đảm bảo không bị null
        double avgRatingVal = currentAvgRating != null ? currentAvgRating : 0.0;
        double prevAvgRatingVal = prevAvgRating != null ? prevAvgRating : 0.0;

        // Tính mức độ tăng trưởng điểm đánh giá
        double ratingGrowth = 0.0;
        if (avgRatingVal > 0 && prevAvgRatingVal > 0) {
            ratingGrowth = avgRatingVal - prevAvgRatingVal;
        } else if (avgRatingVal > 0 && prevAvgRatingVal == 0) {
            ratingGrowth = avgRatingVal; // Có review đầu tiên
        }

        // ==========================================
        // 4. ĐÓNG GÓI RESPONSE
        // ==========================================
        return HostPortfolioSummaryResponse.builder()
                .totalPortfolioValue(currentRevenue)
                .portfolioGrowthRate(portfolioGrowthRate)
                .averageOccupancyRate(Math.round(avgOccupancy * 10.0) / 10.0)
                .occupancyTrend(occupancyTrend)
                .averageRating(Math.round(avgRatingVal * 100.0) / 100.0)
                .ratingGrowth(Math.round(ratingGrowth * 100.0) / 100.0)
                .totalProperties(totalProperties)
                .build();
    }

    @Override
    public Long getOwnerIdByHomestayId(Long homestayId) {
        return homestayRepository.getOwnerIdByHomestayId(homestayId);
    }



    private List<GlobalSearchResponse> mapHomestaysToResponse(List<Homestay> homestays) {
        if (homestays.isEmpty()) return List.of();

        List<Long> ids = homestays.stream().map(Homestay::getId).toList();
        var roomMap = homestayRoomService.getRoomSummaries(ids).stream()
                .collect(Collectors.toMap(s -> s.getHomestayId(), s -> s));
        var imagesMap = homestayImageService.getImagesForHomestays(ids);
        var locationIds = homestays.stream().map(Homestay::getLocationId).distinct().toList();
        var locationsMap = locationService.getLocationNamesMap(locationIds);

        return homestays.stream().map(h -> {
            var room = roomMap.get(h.getId());
            return new GlobalSearchResponse(
                    h.getId(), h.getName(),
                    locationsMap.get(h.getLocationId()),
                    room != null ? room.getMinPrice() : BigDecimal.ZERO,
                    imagesMap.getOrDefault(h.getId(), List.of()),
                    "HOMESTAY",
                    h.getAverageRating() != null ? h.getAverageRating().doubleValue() : 0.0,
                    room != null ? room.getMaxGuestsInRoom() : 0,
                    room != null ? room.getTotalRooms() : 0
            );
        }).toList();
    }

    private List<GlobalSearchResponse> mapToursToResponse(List<Tour> tours) {
        if (tours.isEmpty()) return List.of();

        List<Long> ids = tours.stream().map(Tour::getId).toList();
        var imagesMap = tourImageService.getImagesForTours(ids);

        return tours.stream().map(t -> new GlobalSearchResponse(
                t.getId(), t.getName(),
                t.getLocationDetail(), // Tour lấy trực tiếp từ location_detail
                t.getPricePerPerson(),
                imagesMap.getOrDefault(t.getId(), List.of()),
                "TOUR",
                5.0, // Tạm fix 5 sao hoặc lấy từ bảng tour review nếu bác có
                t.getMaxParticipants(),
                0 // Tour không có bedrooms
        )).toList();
    }
    private RoomTimelineResponse mapToRoomTimeline(
            HomestayRoom room,
            LocalDate startDate,
            LocalDate endDate,
            Map<LocalDate, RoomCalendar> roomCalendars,                  // Map Tồn phòng của riêng phòng này
            List<RoomRatePlan> roomPlans,                                // Danh sách gói giá của phòng này
            Map<Long, Map<LocalDate, RatePlanCalendar>> rateCalendarMap, // Map Lịch Giá (RatePlanId -> Ngày -> Giá)
            List<BookingTimelineProjection> roomBookings,                // Danh sách Booking của riêng phòng này
            String roomImageUrl,                                         // Ảnh đại diện phòng
            Map<Long, String> ownerAvatarMap                             // Map Avatar của Guest/Owner
    ) {
        // 1. Map dữ liệu Giá và Tồn theo ngày (CHẠY VÒNG LẶP ĐỂ KHÔNG THỦNG TIMELINE)
        List<DailyStatusResponse> dailyStatuses = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            RoomCalendar cal = roomCalendars.get(currentDate);

            // Thuật toán quét giá rẻ nhất cho ngày hiện tại
            BigDecimal minPrice = roomPlans.stream()
                    .map(rp -> {
                        Map<LocalDate, RatePlanCalendar> rpCalMap = rateCalendarMap.getOrDefault(rp.getId(), Collections.emptyMap());
                        // Có đè giá thì lấy giá đè, không thì lấy giá gốc
                        return rpCalMap.containsKey(currentDate) ? rpCalMap.get(currentDate).getPrice() : rp.getPrice();
                    })
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            // Lấy số lượng phòng trống
            int availableQty = (cal != null && cal.getAvailableQuantity() != null)
                    ? cal.getAvailableQuantity()
                    : room.getQuantity();

            dailyStatuses.add(DailyStatusResponse.builder()
                    .date(currentDate)
                    .price(minPrice)
                    .availableQuantity(availableQty)
                    .build());
        }

        // 2. Map dữ liệu Đặt phòng (Các khối BookingBlock)
        List<BookingBlockResponse> bookingBlocks = roomBookings.stream()
                .map(b -> BookingBlockResponse.builder()
                        .bookingId(b.bookingId())
                        .guestName(b.guestName())
                        .avatar(ownerAvatarMap.get(b.userId())) // Map Avatar cực chuẩn
                        .checkInDate(b.checkInDate())
                        .checkOutDate(b.checkOutDate())
                        .status(b.status())
                        .build())
                .toList();

        // 3. Trả về DTO hoàn chỉnh
        return RoomTimelineResponse.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .dailyStatuses(dailyStatuses)
                .bookings(bookingBlocks)
                .imageUrl(roomImageUrl)
                .build();
    }
    /**
     * Thuật toán gom nhóm để tính tỷ lệ lấp đầy thực tế cho hàng loạt Homestay
     */
    private Map<Long, Integer> calculateBatchOccupancy(
            List<Long> homeIds,
            List<HomestayRoom> allRooms,
            List<BookingTimelineProjection> bookings,
            LocalDate startOfMonth,
            LocalDate endOfMonth) {

        int daysInMonth = startOfMonth.lengthOfMonth();
        Map<Long, Integer> resultMap = new HashMap<>();

        // Group 1: Gom phòng theo homestayId
        Map<Long, List<HomestayRoom>> roomsByHomeMap = allRooms.stream()
                .collect(Collectors.groupingBy(HomestayRoom::getHomestayId));

        // Group 2: Gom booking theo roomId
        Map<Long, List<BookingTimelineProjection>> bookingsByRoom = bookings.stream()
                .collect(Collectors.groupingBy(BookingTimelineProjection::roomId));

        for (Long homeId : homeIds) {
            List<HomestayRoom> rooms = roomsByHomeMap.getOrDefault(homeId, List.of());
            if (rooms.isEmpty()) {
                resultMap.put(homeId, 0);
                continue;
            }

            int totalCapacityNights = 0;
            int totalBookedNights = 0;

            for (HomestayRoom room : rooms) {
                int roomQuantity = room.getQuantity() != null ? room.getQuantity() : 1;
                // A. Tính tổng số đêm có thể bán của loại phòng này trong tháng
                totalCapacityNights += roomQuantity * daysInMonth;

                // B. Tính số đêm đã bị khách đặt
                List<BookingTimelineProjection> roomBookings = bookingsByRoom.getOrDefault(room.getId(), List.of());
                for (BookingTimelineProjection b : roomBookings) {
                    if (BookingStatus.CANCELLED.equals(b.status()) || BookingStatus.FAILED.equals(b.status())) {
                        continue;
                    }

                    // Chặn ngày (cắt bớt những ngày nằm ngoài tháng hiện tại để tính chính xác)
                    LocalDate checkIn = b.checkInDate().isBefore(startOfMonth) ? startOfMonth : b.checkInDate();
                    LocalDate checkOut = b.checkOutDate().isAfter(endOfMonth.plusDays(1)) ? endOfMonth.plusDays(1) : b.checkOutDate();

                    long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
                    if (nights > 0) {

                        int qty = b.quantity();
                        totalBookedNights += (int) nights * qty;
                    }
                }
            }

            // C. Chia tỷ lệ và gán vào kết quả
            if (totalCapacityNights == 0) {
                resultMap.put(homeId, 0);
            } else {
                int occupancy = (int) Math.round((totalBookedNights * 100.0) / totalCapacityNights);
                resultMap.put(homeId, Math.min(100, occupancy)); // Khống chế trần 100% để đề phòng overbooking
            }
        }

        return resultMap;
    }
    @Override
    public HomestaySearchResultResponse mapToHomestay(java.sql.ResultSet rs) throws java.sql.SQLException {
        return HomestaySearchResultResponse.builder()
                .roomId(rs.getLong("room_id"))
                .homestayId(rs.getLong("homestay_id"))
                .name(rs.getString("name"))
                .city(rs.getString("city"))
                .price(rs.getBigDecimal("price_current"))
                .bedCount(rs.getInt("bed_count"))
                .maxGuests(rs.getInt("max_guests"))
                .matchScore(1.0 - rs.getDouble("vector_distance"))
                .build();
    }

    @Override
    public List<GlobalSearchResponse> aiHybridSearch(AiSearchRequest request) {
        log.info("[AI INDEX SEARCH] Khởi chạy phễu lọc Hybrid Search...");
        log.info("[AI INDEX SEARCH] Dữ liệu AI gửi xuống: {}", request);

        var spec = HomestaySearchSpec.buildAiSpec(request);
        List<HomestaySearchIndex> candidates = new ArrayList<>();

        try {
            // =================================================================
            // TẦNG 1: LỌC CỨNG TRÊN BẢNG INDEX (Vị trí, Giá, Tiện ích, Sức chứa)
            // =================================================================
            log.info("[AI INDEX SEARCH] Tầng 1: Đang kéo ứng viên từ database...");
            candidates = homestaySearchIndexRepository
                    .findAll(spec, PageRequest.of(0, 50))
                    .getContent();

            log.info("[AI INDEX SEARCH] Tầng 1: Tóm được {} phòng thỏa mãn.", candidates.size());

            if (candidates.isEmpty()) {
                return new ArrayList<>();
            }

            // =================================================================
            // TẦNG 2: CHECK LỊCH TRỐNG (Chỉ chạy khi có ngày Check-in/Check-out)
            // =================================================================
            if (StringUtils.hasText(request.checkInDate()) && StringUtils.hasText(request.checkOutDate())) {
                log.info("[AI INDEX SEARCH] Tầng 2: Có yêu cầu ngày tháng, tiến hành check lịch trống...");

                // Lấy ra danh sách ID của các phòng CÒN TRỐNG
                List<Long> availableRoomIds = filterAvailableRooms(
                        candidates.stream().map(HomestaySearchIndex::getRoomId).toList(),
                        request.checkInDate(),
                        request.checkOutDate()
                );

                // GHI ĐÈ LẠI TẬP CANDIDATES: Chỉ giữ lại những căn còn phòng trống!
                candidates = candidates.stream()
                        .filter(c -> availableRoomIds.contains(c.getRoomId()))
                        .collect(Collectors.toList());

                log.info("[AI INDEX SEARCH] Tầng 2: Sau khi check lịch, còn {} phòng thực sự trống.", candidates.size());
            } else {
                log.info("[AI INDEX SEARCH] Tầng 2: Bỏ qua check lịch (Khách không yêu cầu ngày tháng).");
            }

        } catch (Exception e) {
            log.error("[AI INDEX SEARCH] LỖI RỒI ÔNG ƠI RỚT TẠI TẦNG 1/TẦNG 2: ", e);
            return new ArrayList<>();
        }

        // Nếu sau Tầng 1 và Tầng 2 mà rụng hết (kích thước = 0) thì nghỉ luôn, khỏi quét AI
        if (candidates.isEmpty()) {
            log.info("[AI INDEX SEARCH] Rụng hết ứng viên, kết thúc tìm kiếm.");
            return new ArrayList<>();
        }

        // In log để debug những căn xuất sắc lọt vào vòng trong
        candidates.forEach(c -> log.info(
                "   -> [Lọt vào Tầng 3] roomId={}, homestayId={}, name={}, rating={}",
                c.getRoomId(), c.getHomestayId(), c.getName(), c.getAverageRating()
        ));


        // =================================================================
        // TẦNG 3: TÌM KIẾM NGỮ NGHĨA BẰNG VECTOR
        // =================================================================
        List<HomestaySearchIndex> finalIndexedResults;

        if (StringUtils.hasText(request.semanticQuery())) {
            log.info("[AI INDEX SEARCH] Tầng 3: Quét Vector Semantic với từ khóa: '{}'", request.semanticQuery());

            // Cầm đúng cái list ID đã được lọc sạch sẽ ở trên ném vào
            List<Long> candidateRoomIds = candidates.stream()
                    .map(HomestaySearchIndex::getRoomId)
                    .collect(Collectors.toList());

            // Đổi chuỗi văn bản sang mảng vector số
            float[] embedding = embeddingModel.embed(request.semanticQuery());
            String vectorString = Arrays.toString(embedding).replace(" ", "");

            // Quét vector trên bảng Index
            finalIndexedResults = homestaySearchIndexRepository.findSemanticWithinCandidates(candidateRoomIds, vectorString, 5);
        } else {
            log.info("[AI INDEX SEARCH] Tầng 3: Không có từ khóa AI, xếp hạng theo Rating.");
            // Nếu không có ngữ nghĩa, lấy top phòng theo điểm đánh giá
            finalIndexedResults = candidates.stream()
                    .sorted((r1, r2) -> {
                        BigDecimal rating1 = r1.getAverageRating() != null ? r1.getAverageRating() : BigDecimal.ZERO;
                        BigDecimal rating2 = r2.getAverageRating() != null ? r2.getAverageRating() : BigDecimal.ZERO;
                        return rating2.compareTo(rating1);
                    })
                    .limit(5)
                    .collect(Collectors.toList());
        }

        // Map kết quả ra DTO
        return finalIndexedResults.stream()
                .map(idx -> new GlobalSearchResponse(
                        idx.getHomestayId(),
                        idx.getName(),
                        idx.getCity(),
                        idx.getPriceCurrent(),
                        new ArrayList<>(),
                        "HOMESTAY",
                        idx.getAverageRating() != null ? idx.getAverageRating().doubleValue() : 0.0,
                        idx.getMaxGuests(),
                        idx.getBedCount()
                ))
                .collect(Collectors.toList());
    }
    public List<Long> filterAvailableRooms(List<Long> candidateRoomIds, String checkInStr, String checkOutStr) {
        if (candidateRoomIds == null || candidateRoomIds.isEmpty()) {
            return new ArrayList<>();
        }

        if (checkInStr == null || checkOutStr == null || checkInStr.isBlank() || checkOutStr.isBlank()) {
            return candidateRoomIds;
        }

        try {
            LocalDate checkInDate = LocalDate.parse(checkInStr.trim());
            LocalDate checkOutDate = LocalDate.parse(checkOutStr.trim());

            if (!checkInDate.isBefore(checkOutDate)) {
                return new ArrayList<>(); // Ngày trả phòng phải sau ngày nhận phòng
            }

            return homestayRoomRepository.getAvailableRoomIds(candidateRoomIds, checkInDate, checkOutDate);

        } catch (DateTimeParseException e) {
            log.error("AI trả về sai định dạng ngày: checkIn={}, checkOut={}", checkInStr, checkOutStr);
            return new ArrayList<>();
        }
    }

    @Override
    public clyvasync.Clyvasync.dto.response.YearlyRevenueResponse getYearlyRevenueChart(Long hostId, int year) {
        java.util.List<clyvasync.Clyvasync.dto.projection.MonthlyRevenueProjection> thisYearData = bookingRepository.getMonthlyRevenueByHostAndYear(hostId, year);
        java.util.List<clyvasync.Clyvasync.dto.projection.MonthlyRevenueProjection> lastYearData = bookingRepository.getMonthlyRevenueByHostAndYear(hostId, year - 1);

        java.util.List<java.math.BigDecimal> thisYearRevenue = new java.util.ArrayList<>(java.util.Collections.nCopies(12, java.math.BigDecimal.ZERO));
        java.util.List<java.math.BigDecimal> lastYearRevenue = new java.util.ArrayList<>(java.util.Collections.nCopies(12, java.math.BigDecimal.ZERO));

        for (clyvasync.Clyvasync.dto.projection.MonthlyRevenueProjection proj : thisYearData) {
            if (proj.getMonth() != null && proj.getMonth() >= 1 && proj.getMonth() <= 12) {
                thisYearRevenue.set(proj.getMonth() - 1, proj.getRevenue());
            }
        }

        for (clyvasync.Clyvasync.dto.projection.MonthlyRevenueProjection proj : lastYearData) {
            if (proj.getMonth() != null && proj.getMonth() >= 1 && proj.getMonth() <= 12) {
                lastYearRevenue.set(proj.getMonth() - 1, proj.getRevenue());
            }
        }

        return new clyvasync.Clyvasync.dto.response.YearlyRevenueResponse(thisYearRevenue, lastYearRevenue);
    }
}