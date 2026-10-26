package com.ipocalc.ipocatalog.controller.ipo;

import com.ipocalc.ipocatalog.controller.ipo.dto.IpoInsightResponse;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoResponse;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncRangeRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoSyncStatusResponse;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.ipocalc.ipocatalog.service.ipo.IpoService;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailService;
import com.ipocalc.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoManualSyncService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** IPO(공모주) 기준정보에 대한 등록/조회/수정/비활성화 API를 제공한다. */
@RestController
@RequestMapping("/ipos")
@RequiredArgsConstructor
public class IpoController {

  private final IpoService ipoService;
  private final IpoDetailService ipoDetailService;
  private final IpoDetailSyncService ipoDetailSyncService;
  private final IpoManualSyncService ipoManualSyncService;

  /** 비활성화되지 않은(삭제되지 않은) IPO 목록을 최신 기준·생성일 내림차순으로 조회해 반환한다. */
  @GetMapping
  public ResponseEntity<List<IpoResponse>> list() {
    List<IpoResponse> responses = ipoService.list();
    return ResponseEntity.ok(responses);
  }

  /** IPO ID로 단건 IPO 정보를 조회한다. 존재하지 않거나 비활성화되었으면 404를 반환한다. */
  @GetMapping("/{id}")
  public ResponseEntity<IpoResponse> getById(@PathVariable Long id) {
    IpoResponse response = ipoService.getById(id);
    return ResponseEntity.ok(response);
  }

  /**
   * 공모주 상세 화면의 부가 정보(기업정보·수요예측·증권사별 청약·최근 실적)를 조회한다. 로그인 없이 열린다(GET /ipos/**).
   *
   * @param id 공모주 ID
   * @return 부가 정보(모르는 값은 null). 없는 공모주는 404
   */
  @GetMapping("/{id}/insight")
  public ResponseEntity<IpoInsightResponse> getInsight(@PathVariable Long id) {
    return ResponseEntity.ok(ipoDetailService.getInsight(id));
  }

  /**
   * 공모주 목록(DART·일정 병합)과 상세 정보를 지금 바로 가져온다. 관리자 전용(POST /ipos/**). 수십 초~수 분 걸린다.
   *
   * @param request 범위 지정 요청 (fromMonth, toMonth: YYYY-MM). 비어 있으면 기본 동작(최근 N일 + 상세 동기화).
   * @return 범위 지정 시 202 + 상태 객체(state=RUNNING), 기본 동작 시 200 + 결과. 이미 실행 중이면 409, 키 없으면 503, 범위 오류면
   *     400.
   */
  @PostMapping("/sync")
  public ResponseEntity<?> syncNow(
      @Valid @RequestBody(required = false) IpoSyncRangeRequest request) {
    if (request != null && request.hasRange()) {
      IpoSyncStatusResponse status = ipoManualSyncService.syncRange(request);
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }
    return ResponseEntity.ok(ipoManualSyncService.syncNow());
  }

  /**
   * 공모주 동기화 진행 상태를 조회한다. 관리자 전용(GET /ipos/sync/**).
   *
   * @return 상태 객체. 한 번도 실행 안 했으면 state=IDLE.
   */
  @GetMapping("/sync/status")
  public ResponseEntity<IpoSyncStatusResponse> getSyncStatus() {
    return ResponseEntity.ok(ipoManualSyncService.getSyncStatus());
  }

  /**
   * 공모주 상세 정보(DART)를 지금 바로 동기화한다. 관리자 전용(POST /ipos/**). 평소에는 매일 05:00에 자동으로 돈다.
   *
   * @return 새로 저장한 공모주 수
   */
  @PostMapping("/insight/sync")
  public ResponseEntity<Map<String, Integer>> syncInsight() {
    return ResponseEntity.ok(Map.of("saved", ipoDetailSyncService.syncRecent()));
  }

  /** IPO ID로 해당 IPO의 회사명/공모명 등을 수정하고, 수정된 IPO 정보를 반환한다. 존재하지 않으면 404를 반환한다. */
  @PatchMapping("/{id}")
  public ResponseEntity<IpoResponse> update(
      @PathVariable Long id, @RequestBody IpoUpdateRequest request) {
    IpoResponse response = ipoService.update(id, request);
    return ResponseEntity.ok(response);
  }

  /** IPO ID로 해당 IPO를 비활성화 상태로 전환하고, 204 No Content를 반환한다. 존재하지 않으면 404를 반환한다. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(@PathVariable Long id) {
    ipoService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  /** IPO 등록 요청을 받아 새 IPO를 생성하고, 201 Created와 함께 생성된 IPO 정보를 반환한다. */
  @PostMapping
  public ResponseEntity<IpoResponse> register(@RequestBody IpoRegisterRequest request) {
    IpoResponse response = ipoService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
