package com.ipocalc.announcements.service;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.util.security.SecurityUtils;
import com.ipocalc.announcements.controller.AnnouncementRequest;
import com.ipocalc.announcements.controller.AnnouncementResponse;
import com.ipocalc.announcements.domain.Announcement;
import com.ipocalc.announcements.repository.AnnouncementDismissalRepository;
import com.ipocalc.announcements.repository.AnnouncementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 공지사항 관리자·사용자 유스케이스. */
@Service
public class AnnouncementService {
  private final AnnouncementRepository repository;
  private final AnnouncementDismissalRepository dismissalRepository;
  private final SecurityUtils securityUtils;
  private final Clock clock;

  /** 필요한 저장소와 서울 시간 시계를 주입받는다. */
  public AnnouncementService(
      AnnouncementRepository repository,
      AnnouncementDismissalRepository dismissalRepository,
      SecurityUtils securityUtils,
      Clock clock) {
    this.repository = repository;
    this.dismissalRepository = dismissalRepository;
    this.securityUtils = securityUtils;
    this.clock = clock;
  }

  /** 관리자 목록을 조회한다. */
  @Transactional(readOnly = true)
  public Page<AnnouncementResponse> adminList(Pageable pageable) {
    return repository
        .findAllByDeletedAtIsNullOrderByStartAtAscIdAsc(pageable)
        .map(AnnouncementResponse::from);
  }

  /** 상태 조건으로 관리자 공지사항 목록을 조회한다. */
  @Transactional(readOnly = true)
  public Page<AnnouncementResponse> adminList(RecordStatus status, Pageable pageable) {
    return repository
        .findAllByStatusAndDeletedAtIsNullOrderByStartAtAscIdAsc(status, pageable)
        .map(AnnouncementResponse::from);
  }

  /** 관리자가 ID로 공지사항을 조회한다. */
  @Transactional(readOnly = true)
  public AnnouncementResponse getAdmin(Long id) {
    return AnnouncementResponse.from(findExistingAnnouncement(id));
  }

  /** 활성 기간 내 공지를 조회한다. */
  @Transactional(readOnly = true)
  @SuppressWarnings("checkstyle:LineLength")
  public Page<AnnouncementResponse> listActive(Pageable pageable) {
    LocalDate today = LocalDate.now(clock);
    return repository
        .findAllByStatusAndDeletedAtIsNullAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtAscIdAsc(
            RecordStatus.ACTIVE, today, today, pageable)
        .map(AnnouncementResponse::from);
  }

  /** 영구 숨김 처리되지 않은 활성 공지를 조회한다. */
  @Transactional(readOnly = true)
  public List<AnnouncementResponse> popup() {
    Long accountId = securityUtils.getCurrentAccountId();
    Set<Long> dismissed = new HashSet<>();
    dismissalRepository
        .findAllByAccountId(accountId)
        .forEach(item -> dismissed.add(item.getAnnouncementId()));
    LocalDate today = LocalDate.now(clock);
    return active(today).stream()
        .filter(item -> !dismissed.contains(item.getId()))
        .map(AnnouncementResponse::from)
        .toList();
  }

  /** 사용자에게 공개할 공지사항 하나를 조회한다. */
  @Transactional(readOnly = true)
  public AnnouncementResponse getActive(Long id) {
    LocalDate today = LocalDate.now(clock);
    return repository
        .findByIdAndStatusAndDeletedAtIsNullAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            id, RecordStatus.ACTIVE, today, today)
        .map(AnnouncementResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."));
  }

  /** 관리자 공지사항을 생성한다. */
  @Transactional
  public AnnouncementResponse create(AnnouncementRequest request) {
    validate(request);
    LocalDateTime now = LocalDateTime.now(clock);
    Announcement item =
        Announcement.create(
            request.title().trim(),
            request.content(),
            request.startAt(),
            request.endAt(),
            request.status() == null ? RecordStatus.ACTIVE : request.status(),
            now);
    return AnnouncementResponse.from(repository.save(item));
  }

  /** 관리자 공지사항을 수정한다. */
  @Transactional
  public AnnouncementResponse update(Long id, AnnouncementRequest request) {
    validate(request);
    Announcement item = findExistingAnnouncement(id);
    item.edit(
        request.title().trim(),
        request.content(),
        request.startAt(),
        request.endAt(),
        request.status(),
        LocalDateTime.now(clock));
    return AnnouncementResponse.from(repository.save(item));
  }

  /** 공지사항을 논리 삭제한다. */
  @Transactional
  public void delete(Long id) {
    Announcement item = findExistingAnnouncement(id);
    LocalDateTime now = LocalDateTime.now(clock);
    item.cancel(now);
    repository.save(item);
  }

  /** 계정 기준으로 공지를 영구 숨김 처리한다. */
  @Transactional
  public void dismiss(Long id) {
    getActive(id);
    Long accountId = securityUtils.getCurrentAccountId();
    dismissalRepository.insertIfAbsent(accountId, id, LocalDateTime.now(clock));
  }

  @SuppressWarnings("checkstyle:LineLength")
  private List<Announcement> active(LocalDate today) {
    return repository
        .findAllByStatusAndDeletedAtIsNullAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtAscIdAsc(
            RecordStatus.ACTIVE, today, today);
  }

  private Announcement findExistingAnnouncement(Long id) {
    return repository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."));
  }

  private void validate(AnnouncementRequest request) {
    if (request == null
        || request.title() == null
        || request.title().isBlank()
        || request.title().trim().length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 1~200자로 입력해 주세요.");
    }
    if (request.content() == null
        || request.content().isBlank()
        || request.content().length() > 10000) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내용은 1~10000자로 입력해 주세요.");
    }
    if (request.startAt() == null
        || request.endAt() == null
        || request.startAt().isAfter(request.endAt())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시 시작일과 종료일을 올바르게 입력해 주세요.");
    }
    if (request.status() != null
        && request.status() != RecordStatus.ACTIVE
        && request.status() != RecordStatus.CANCELLED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상태는 ACTIVE 또는 CANCELLED여야 합니다.");
    }
  }
}
