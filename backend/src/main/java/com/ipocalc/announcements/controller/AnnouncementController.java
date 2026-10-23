package com.ipocalc.announcements.controller;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.announcements.service.AnnouncementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 로그인 사용자 공지 조회와 관리자 공지 관리 API. */
@RestController
@RequiredArgsConstructor
public class AnnouncementController {
  private static final int MAX_PAGE = 100_000;
  private static final int MAX_SIZE = 100;

  private final AnnouncementService service;

  /** 게시 중인 공지 목록을 반환한다. */
  @GetMapping("/announcements")
  public Page<AnnouncementResponse> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.listActive(pageable(page, size));
  }

  /** 게시 중인 공지 하나를 반환한다. */
  @GetMapping("/announcements/{id}")
  public AnnouncementResponse get(@PathVariable Long id) {
    return service.getActive(id);
  }

  /** 팝업 노출 대상 공지를 반환한다. */
  @GetMapping("/announcements/popup")
  public ResponseEntity<List<AnnouncementResponse>> popup() {
    List<AnnouncementResponse> announcements = service.popup();
    return announcements.isEmpty()
        ? ResponseEntity.noContent().build()
        : ResponseEntity.ok(announcements);
  }

  /** 계정별로 공지를 영구 숨긴다. */
  @PostMapping("/announcements/{id}/dismiss")
  public ResponseEntity<Void> dismiss(@PathVariable Long id) {
    service.dismiss(id);
    return ResponseEntity.ok().build();
  }

  /** 관리자 전체 목록을 반환한다. */
  @GetMapping("/admin/announcements")
  public Page<AnnouncementResponse> adminList(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) RecordStatus status) {
    Pageable pageable = pageable(page, size);
    if (status == null) {
      return service.adminList(pageable);
    }
    if (status == RecordStatus.INACTIVE) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상태는 ACTIVE 또는 CANCELLED여야 합니다.");
    }
    return service.adminList(status, pageable);
  }

  /** 관리자가 ID로 공지사항 하나를 조회한다. */
  @GetMapping("/admin/announcements/{id}")
  public AnnouncementResponse adminGet(@PathVariable Long id) {
    return service.getAdmin(id);
  }

  /** 관리자 공지를 생성한다. */
  @PostMapping("/admin/announcements")
  public ResponseEntity<AnnouncementResponse> create(@RequestBody AnnouncementRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 관리자 공지를 수정한다. */
  @PatchMapping("/admin/announcements/{id}")
  public AnnouncementResponse update(
      @PathVariable Long id, @RequestBody AnnouncementRequest request) {
    return service.update(id, request);
  }

  /** 관리자 공지를 논리 삭제한다. */
  @DeleteMapping("/admin/announcements/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  private Pageable pageable(int page, int size) {
    if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1~100이어야 합니다.");
    }
    return Pageable.ofSize(size).withPage(page);
  }
}
