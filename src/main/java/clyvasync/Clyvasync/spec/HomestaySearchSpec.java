package clyvasync.Clyvasync.spec;

import clyvasync.Clyvasync.dto.record.AiSearchRequest;
import clyvasync.Clyvasync.dto.record.PolicyFilterRequest;
import clyvasync.Clyvasync.dto.request.GlobalSearchRequest;
import clyvasync.Clyvasync.modules.homestay.entity.*;
import clyvasync.Clyvasync.modules.room.RoomRatePlan;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HomestaySearchSpec {

    public static Specification<Homestay> buildGlobalSpec(GlobalSearchRequest filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. TÌM TỪ KHÓA (Gần đúng)
            if (StringUtils.hasText(filters.keyword())) {
                String searchPattern = "%" + filters.keyword().trim().toLowerCase() + "%";

                Subquery<Integer> locSub = query.subquery(Integer.class);
                Root<Location> locRoot = locSub.from(Location.class);
                locSub.select(locRoot.get("id"));
                locSub.where(cb.like(cb.lower(cb.function("unaccent", String.class, locRoot.get("cityName"))), cb.function("unaccent", String.class, cb.literal(searchPattern))));

                Predicate matchLocation = root.get("locationId").in(locSub);
                Predicate matchName = cb.like(cb.lower(cb.function("unaccent", String.class, root.get("name"))), cb.function("unaccent", String.class, cb.literal(searchPattern)));

                predicates.add(cb.or(matchName, matchLocation));
            }

//            // 2. LỌC TIỆN ÍCH (Sử dụng EXISTS là chuẩn, giữ nguyên)
//            if (filters.amenityIds() != null && !filters.amenityIds().isEmpty()) {
//                for (Integer amId : filters.amenityIds()) {
//                    Subquery<Integer> amSub = query.subquery(Integer.class);
//                    Root<HomestayAmenity> amRoot = amSub.from(HomestayAmenity.class);
//                    amSub.select(cb.literal(1));
//                    amSub.where(
//                            cb.equal(amRoot.get("homestayId"), root.get("id")),
//                            cb.equal(amRoot.get("amenityId"), amId)
//                    );
//                    predicates.add(cb.exists(amSub));
//                }
//            }

//            // 3. LỌC GIÁ MAX (Chỉ lọc nếu có giá trị)
            if (filters.maxPrice() != null && filters.maxPrice().doubleValue() > 0) {
                Subquery<Long> roomSub = query.subquery(Long.class);
                Root<HomestayRoom> roomRoot = roomSub.from(HomestayRoom.class);
                roomSub.select(roomRoot.get("id"));
                roomSub.where(cb.equal(roomRoot.get("homestayId"), root.get("id")));

                Subquery<Integer> rateSub = query.subquery(Integer.class);
                Root<RoomRatePlan> rateRoot = rateSub.from(RoomRatePlan.class);
                rateSub.select(cb.literal(1));
                rateSub.where(
                        rateRoot.get("roomId").in(roomSub),
                        cb.lessThanOrEqualTo(rateRoot.get("price"), filters.maxPrice())
                );
                predicates.add(cb.exists(rateSub));
            }

            // 4. LỌC KHÁCH & PHÒNG (Sửa logic > 0 để tránh lọc mất dữ liệu)
            if ((filters.guests() != null && filters.guests() > 0) || (filters.bedrooms() != null && filters.bedrooms() > 0)) {
                Subquery<Integer> roomDetailSub = query.subquery(Integer.class);
                Root<HomestayRoom> detailRoot = roomDetailSub.from(HomestayRoom.class);
                roomDetailSub.select(cb.literal(1));

                List<Predicate> roomPreds = new ArrayList<>();
                roomPreds.add(cb.equal(detailRoot.get("homestayId"), root.get("id")));

                if (filters.guests() != null && filters.guests() > 0) {
                    roomPreds.add(cb.greaterThanOrEqualTo(detailRoot.get("maxGuests"), filters.guests()));
                }
                if (filters.bedrooms() != null && filters.bedrooms() > 0) {
                    roomPreds.add(cb.greaterThanOrEqualTo(detailRoot.get("bedCount"), filters.bedrooms()));
                }
                roomDetailSub.where(cb.and(roomPreds.toArray(new Predicate[0])));
                predicates.add(cb.exists(roomDetailSub));
            }

            // 5. RATING
            if (filters.minRating() != null && filters.minRating() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("averageRating"), filters.minRating()));
            }

            // 6. STATUS (Bỏ check deletedAt nếu DB không có, hoặc đảm bảo status là AVAILABLE)
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), "APPROVED"));

            // Thay đoạn log cũ bằng đoạn này
            log.info("DEBUG: Generated predicates count: {}", predicates.size());
            for (int i = 0; i < predicates.size(); i++) {
                log.info("DEBUG: Predicate [{}]: {}", i, predicates.get(i).toString());
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    public static Specification<HomestaySearchIndex> buildAiSpec(AiSearchRequest filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. LỌC ĐỊA ĐIỂM
            if (StringUtils.hasText(filters.location())) {
                String searchPattern = "%" + filters.location().trim().toLowerCase() + "%";
                System.out.println(searchPattern);
                Predicate matchCity = cb.like(cb.lower(cb.function("unaccent", String.class, root.get("city"))),
                        cb.function("unaccent", String.class, cb.literal(searchPattern)));
                predicates.add(cb.or(matchCity));
            }
            if (StringUtils.hasText(filters.homestayName())) {
                Expression<String> normalizedName = cb.lower(
                        cb.function("unaccent", String.class, root.get("name"))
                );

                String[] tokens = filters.homestayName()
                        .trim()
                        .toLowerCase()
                        .split("\\s+");

                List<Predicate> namePredicates = new ArrayList<>();
                for (String token : tokens) {
                    String tokenPattern = "%" + token + "%";
                    namePredicates.add(cb.like(
                            normalizedName,
                            cb.function("unaccent", String.class, cb.literal(tokenPattern))
                    ));
                }

                predicates.add(cb.and(namePredicates.toArray(new Predicate[0])));
            }

            // 2. LỌC GIÁ, KHÁCH, GIƯỜNG (Dùng thẳng các trường flat trong index)
//            if (filters.minPrice() != null) {
//                predicates.add(cb.greaterThanOrEqualTo(root.get("priceCurrent"), filters.minPrice()));
//            }
//            if (filters.maxPrice() != null) {
//                predicates.add(cb.lessThanOrEqualTo(root.get("priceCurrent"), filters.maxPrice()));
//            }
            if (filters.guests() != null && filters.guests() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxGuests"), filters.guests()));
            }
            if (filters.bedCount() != null && filters.bedCount() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bedCount"), filters.bedCount()));
            }

         //    3. LỌC TIỆN ÍCH CỨNG (Quét mảng integer[] bằng array_position)
            if (filters.amenityIds() != null && !filters.amenityIds().isEmpty()) {
                predicates.add(cb.isNotNull(root.get("amenityIds")));

                for (Integer amenityId : filters.amenityIds()) {
                    Expression<Integer> arrayPosFunc = cb.function(
                            "array_position",
                            Integer.class,
                            root.get("amenityIds"),
                            cb.literal(amenityId)
                    );

                    predicates.add(cb.isNotNull(arrayPosFunc));
                }
            }

            // 4. LỌC CHÍNH SÁCH (Móc nối Subquery từ homestayId sang bảng homestay_policies)
//            if (filters.policyFilter() != null) {
//                PolicyFilterRequest p = filters.policyFilter();
//                Subquery<Integer> policySubquery = query.subquery(Integer.class);
//                Root<HomestayPolicy> policyRoot = policySubquery.from(HomestayPolicy.class);
//
//                List<Predicate> policyPredicates = new ArrayList<>();
//                // Khớp mã: policy.homestay_id = index.homestay_id
//                policyPredicates.add(cb.equal(policyRoot.get("homestayId"), root.get("homestayId")));
//
//                if (Boolean.TRUE.equals(p.allowsPets())) {
//                    policyPredicates.add(cb.isTrue(policyRoot.get("allowsPets")));
//                }
//                if (Boolean.TRUE.equals(p.allowsSmoking())) {
//                    policyPredicates.add(cb.isTrue(policyRoot.get("allowsSmoking")));
//                }
//                if (Boolean.TRUE.equals(p.allowsParties())) {
//                    policyPredicates.add(cb.isTrue(policyRoot.get("allowsParties")));
//                }
//                if (Boolean.TRUE.equals(p.allowsChildren())) {
//                    policyPredicates.add(cb.isTrue(policyRoot.get("allowsChildren")));
//                }
//                if (Boolean.TRUE.equals(p.noDeposit())) {
//                    policyPredicates.add(cb.isFalse(policyRoot.get("depositRequired")));
//                }
//
//                if (policyPredicates.size() > 1) {
//                    policySubquery.select(cb.literal(1)).where(cb.and(policyPredicates.toArray(new Predicate[0])));
//                    predicates.add(cb.exists(policySubquery));
//                }
//            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}