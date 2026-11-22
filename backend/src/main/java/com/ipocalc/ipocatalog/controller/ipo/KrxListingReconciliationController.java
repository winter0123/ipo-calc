package com.ipocalc.ipocatalog.controller.ipo;

import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingApplyRequest;
import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingPreviewRequest;
import com.ipocalc.ipocatalog.service.ipo.krx.KrxListingReconciliationPreview;
import com.ipocalc.ipocatalog.service.ipo.krx.KrxListingReconciliationService;
import com.ipocalc.ipocatalog.service.ipo.krx.KrxListingReconciliationService.KrxListingApplyResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** KIND 추가상장 공고 관리자 API. */
@RestController
@RequestMapping("/ipos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KrxListingReconciliationController {

  private final KrxListingReconciliationService service;

  /** KIND 공고 미리보기를 수행한다. */
  @PostMapping("/{id}/krx-listing/preview")
  public ResponseEntity<KrxListingReconciliationPreview> preview(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) KrxListingPreviewRequest request) {
    String url = request == null ? null : request.noticeUrl();
    return ResponseEntity.ok(service.preview(id, url));
  }

  /** KIND 공고 후보를 적용한다. */
  @PostMapping("/{id}/krx-listing/apply")
  public ResponseEntity<KrxListingApplyResult> apply(
      @PathVariable Long id, @Valid @RequestBody(required = false) KrxListingApplyRequest request) {
    return ResponseEntity.ok(service.apply(id, request));
  }
}
