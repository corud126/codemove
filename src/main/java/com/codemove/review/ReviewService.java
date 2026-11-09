package com.codemove.review;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.code.CodeService;
import com.codemove.domain.Review;
import com.codemove.domain.ReviewPhoto;
import com.codemove.reservation.ReservationService;

import lombok.RequiredArgsConstructor;

/**
 * 후기 (TBL_REVIEW · TBL_REVIEW_PHOTO).
 * 회원은 본인 예약 중 이사 완료(02)이고 후기가 없는 예약에 1번 쓴다 (별점 1~5 · 내용 500자 · 사진 최대 5장).
 * 관리자는 답변을 등록 · 수정 · 삭제한다 (삭제하면 답변 대기). 답변 알림은 없다 — 사용자 후기 목록에 '사장님 답변'으로 표시 (ERD 4단계).
 * 할 수 없는 동작이면 화면에 보여줄 문구로 IllegalArgumentException(입력 · 대상 없음) / IllegalStateException(상태).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

	public static final int MAX_CONTENT = 500;
	public static final int MAX_PHOTOS = 5;
	private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	private static final DateTimeFormatter USER_DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd");
	private static final DateTimeFormatter ADMIN_DATE = DateTimeFormatter.ofPattern("yy.MM.dd");

	private final ReviewMapper reviewMapper;
	private final ReviewPhotoStorage storage;
	private final ReservationService reservationService;
	private final CodeService codeService;

	/** 사진 한 장 최대 크기(MB) */
	@Value("${codemove.review.max-photo-mb:10}")
	private long maxPhotoMb;

	/* ---------- 회원 ---------- */

	/** 후기 등록. 만든 후기 ID */
	@Transactional
	public Long write(Long memberId, Long reservationId, Integer rating, String content, List<MultipartFile> photos) {
		if (rating == null || rating < 1 || rating > 5) {
			throw new IllegalArgumentException("별점을 선택해 주세요.");
		}
		String text = content == null ? "" : content.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("후기 내용을 입력해 주세요.");
		}
		if (text.length() > MAX_CONTENT) {
			throw new IllegalArgumentException("후기는 " + MAX_CONTENT + "자까지 입력할 수 있어요.");
		}
		List<MultipartFile> files = photos == null ? List.of() : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (files.size() > MAX_PHOTOS) {
			throw new IllegalArgumentException("사진은 " + MAX_PHOTOS + "장까지 올릴 수 있어요.");
		}
		for (MultipartFile f : files) {
			if (f.getContentType() == null || !PHOTO_TYPES.contains(f.getContentType().toLowerCase())) {
				throw new IllegalArgumentException("사진은 JPG · PNG · WEBP 파일만 올릴 수 있어요.");
			}
			if (f.getSize() > maxPhotoMb * 1024 * 1024) {
				throw new IllegalArgumentException("사진은 한 장에 " + maxPhotoMb + "MB까지 올릴 수 있어요.");
			}
		}

		reservationService.completeOverdue();
		ReviewRow target = reservationId == null ? null : reviewMapper.selectReservation(reservationId, memberId);
		if (target == null) {
			throw new IllegalArgumentException("예약을 찾을 수 없어요.");
		}
		if (!ReservationService.COMPLETED.equals(target.getReservationStatus())) {
			throw new IllegalStateException("이사가 끝난 뒤에 후기를 쓸 수 있어요.");
		}
		if (target.getReviewId() != null) {
			throw new IllegalStateException("이미 후기를 쓴 이사예요.");
		}

		List<String> saved = new ArrayList<>();
		try {
			Review review = new Review();
			review.setReservationId(reservationId);
			review.setRating(rating);
			review.setContent(text);
			reviewMapper.insertReview(review);
			int order = 1;
			for (MultipartFile f : files) {
				String path;
				try (InputStream in = f.getInputStream()) {
					path = storage.save(in, f.getOriginalFilename()).toString();
				}
				saved.add(path);
				ReviewPhoto photo = new ReviewPhoto();
				photo.setReviewId(review.getReviewId());
				photo.setFilePath(path);
				photo.setSortOrder(order++);
				reviewMapper.insertPhoto(photo);
			}
			// 새 후기 알림: 공통코드 NTF_DETAIL 에 해당 종류가 없어 보내지 않음 (필요하면 코드 추가)
			return review.getReviewId();
		} catch (DuplicateKeyException e) {
			saved.forEach(storage::delete);
			throw new IllegalStateException("이미 후기를 쓴 이사예요.");
		} catch (IOException e) {
			saved.forEach(storage::delete);
			throw new IllegalStateException("사진을 저장하지 못했어요. 다시 시도해 주세요.");
		} catch (RuntimeException e) {
			saved.forEach(storage::delete);
			throw e;
		}
	}

	/** 내가 쓴 후기 (최근 순) */
	public List<MyReviewView> myReviews(Long memberId) {
		Map<Long, List<ReviewPhoto>> photos = groupPhotos(reviewMapper.selectPhotos(memberId));
		LocalDateTime now = LocalDateTime.now();
		return reviewMapper.selectRows(memberId).stream()
				.map(r -> new MyReviewView(r.getReviewId(), stars(r.getRating()),
						Duration.between(r.getCreatedAt(), now).toSeconds() < 60 ? "방금 전" : r.getCreatedAt().format(USER_DATE),
						district(r.getFromAddress()) + " → " + district(r.getToAddress()), r.getContent(),
						houseShort(r.getHouseType()) + " · " + packName(r.getPackType()),
						photos.getOrDefault(r.getReviewId(), List.of()).stream().map(p -> "/more/reviews/photos/" + p.getPhotoId()).toList(),
						r.getReplyContent(), r.getRepliedAt() == null ? null : r.getRepliedAt().format(USER_DATE)))
				.toList();
	}

	/**
	 * 후기를 쓸 예약. reservationId 가 있으면 그 예약(본인 · 이사 완료 · 후기 없음일 때만), 없으면 가장 최근 이사 완료 예약.
	 * 쓸 수 없으면 null
	 */
	@Transactional
	public ReviewTargetView target(Long memberId, Long reservationId) {
		reservationService.completeOverdue();
		ReviewRow row;
		if (reservationId == null) {
			row = reviewMapper.selectReviewable(memberId).stream().findFirst().orElse(null);
		} else {
			row = reviewMapper.selectReservation(reservationId, memberId);
			if (row != null && (!ReservationService.COMPLETED.equals(row.getReservationStatus()) || row.getReviewId() != null)) {
				row = null;
			}
		}
		if (row == null) {
			return null;
		}
		LocalDate d = row.getMoveDate();
		String date = d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
		String from = district(row.getFromAddress());
		String to = district(row.getToAddress());
		return new ReviewTargetView(row.getReservationId(), date, from, to,
				from + " → " + to + " · " + houseShort(row.getHouseType()) + " " + packName(row.getPackType()) + " · " + date);
	}

	/** 회원 본인 후기 사진 (아니면 null) */
	public ReviewPhoto memberPhoto(Long memberId, Long photoId) {
		return reviewMapper.selectPhoto(photoId, memberId);
	}

	/* ---------- 홈 ---------- */

	/** 홈 '실제 고객 후기': 최근 후기 limit 건 */
	public List<HomeReview> latestForHome(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		LocalDate today = LocalDate.now();
		return reviewMapper.selectLatest(limit).stream()
				.map(r -> new HomeReview(maskName(r.getMemberName()),
						"이용 " + Math.max(1, r.getUseCount() == null ? 1 : r.getUseCount()) + "회차 · "
								+ ago(r.getCreatedAt().toLocalDate(), today),
						houseShort(r.getHouseType()) + "(" + packName(r.getPackType()) + ")", stars(r.getRating()), r.getContent()))
				.toList();
	}

	/* ---------- 관리자 ---------- */

	/** 리뷰 관리 목록 (최근 작성 순, 전체) */
	public List<AdminReviewView> adminList() {
		Map<Long, List<ReviewPhoto>> photos = groupPhotos(reviewMapper.selectPhotos(null));
		return reviewMapper.selectRows(null).stream()
				.map(r -> new AdminReviewView(r.getReviewId(), r.getMemberName(), r.getCreatedAt().format(ADMIN_DATE),
						nv(codeService.name("HOUSE_TYPE", r.getHouseType())), nv(codeService.name("PACK_TYPE", r.getPackType())),
						district(r.getFromAddress()), district(r.getToAddress()), r.getRating(), r.getContent(),
						r.getRepliedAt() == null ? "-" : r.getRepliedAt().format(ADMIN_DATE), r.getReplyContent(),
						photos.getOrDefault(r.getReviewId(), List.of()).stream()
								.map(p -> new AdminReviewView.Photo("/admin/reviews/photos/" + p.getPhotoId(), "center")).toList()))
				.toList();
	}

	/** 답변 등록 · 수정. 답변일(YY.MM.DD) */
	@Transactional
	public String reply(Long reviewId, Long adminId, String content) {
		String text = content == null ? "" : content.trim();
		if (text.isEmpty()) {
			throw new IllegalArgumentException("답변 내용을 입력해 주세요.");
		}
		if (text.length() > MAX_CONTENT) {
			throw new IllegalArgumentException("답변은 " + MAX_CONTENT + "자까지 입력할 수 있어요.");
		}
		if (reviewMapper.updateReply(reviewId, text, adminId) == 0) {
			throw new IllegalArgumentException("리뷰를 찾을 수 없어요.");
		}
		// 고객 알림 없음 (ERD 4단계) — 사용자 후기 목록에 '사장님 답변'으로 표시
		return reviewMapper.selectById(reviewId).getRepliedAt().format(ADMIN_DATE);
	}

	/** 답변 삭제 → 답변 대기 */
	@Transactional
	public void deleteReply(Long reviewId) {
		if (reviewMapper.deleteReply(reviewId) == 0) {
			throw new IllegalArgumentException("리뷰를 찾을 수 없어요.");
		}
	}

	/** 후기 사진 (관리자, 없으면 null) */
	public ReviewPhoto photo(Long photoId) {
		return reviewMapper.selectPhoto(photoId, null);
	}

	/* ---------- 표시값 ---------- */

	private static Map<Long, List<ReviewPhoto>> groupPhotos(List<ReviewPhoto> photos) {
		return photos.stream().collect(Collectors.groupingBy(ReviewPhoto::getReviewId, Collectors.mapping(Function.identity(), Collectors.toList())));
	}

	/** ★★★★☆ */
	static String stars(int rating) {
		return "★".repeat(rating) + "☆".repeat(5 - rating);
	}

	/** 김코드 → 김*드, 이준 → 이*, 남궁민수 → 남**수 */
	static String maskName(String name) {
		if (name == null || name.isBlank()) {
			return "고객";
		}
		String n = name.trim();
		if (n.length() == 1) {
			return n;
		}
		if (n.length() == 2) {
			return n.charAt(0) + "*";
		}
		return n.charAt(0) + "*".repeat(n.length() - 2) + n.charAt(n.length() - 1);
	}

	/** 오늘 · 어제 · N일 전 · N주 전 · N개월 전 · N년 전 */
	static String ago(LocalDate date, LocalDate today) {
		long days = ChronoUnit.DAYS.between(date, today);
		if (days <= 0) {
			return "오늘";
		}
		if (days == 1) {
			return "어제";
		}
		if (days < 7) {
			return days + "일 전";
		}
		if (days < 30) {
			return days / 7 + "주 전";
		}
		if (days < 365) {
			return days / 30 + "개월 전";
		}
		return days / 365 + "년 전";
	}

	/** 사용자 주거 형태 앞부분: 원룸 / 오피스텔 → 원룸, 빌라 / 주택 → 빌라 */
	private String houseShort(String houseType) {
		String name = nv(codeService.userName("HOUSE_TYPE", houseType));
		int slash = name.indexOf(" /");
		return slash > 0 ? name.substring(0, slash) : name;
	}

	/** 사용자 포장 방식: 일반포장 · 반포장 · 보관이사 · 포장이사 */
	private String packName(String packType) {
		return nv(codeService.userName("PACK_TYPE", packType));
	}

	/** 주소에서 구 · 군 이름 (서울 강남구 테헤란로 123 → 강남구). ReservationService.district 와 같은 규칙 */
	static String district(String address) {
		if (address == null || address.isBlank()) {
			return "";
		}
		String[] parts = address.trim().split("\\s+");
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].endsWith("구") || parts[i].endsWith("군")) {
				return parts[i];
			}
		}
		return parts.length > 1 ? parts[1] : parts[0];
	}

	private static String nv(String s) {
		return s == null ? "" : s;
	}
}
