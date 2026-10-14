package com.vibeaiteam.ipocatalog.service.ipo.sync;

import com.vibeaiteam.ipocatalog.exception.OpenDartKeyMissingException;
import com.vibeaiteam.ipocatalog.service.ipo.detail.IpoDetailSyncService;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 관리자 "지금 가져오기". 새벽 스케줄러(04:30 목록·일정, 05:00 상세)와 같은 서비스를 같은 순서로 부른다.
 * 기간은 스케줄러와 같은 {@code app.ipo-sync.days-back}(기본 90일)이라 빈 DB도 한 번에 채운다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpoManualSyncService {

  private final IpoSyncService ipoSyncService;
  private final IpoDetailSyncService ipoDetailSyncService;
  private final OpenDartApiProperties openDartProperties;

  @Value("${app.ipo-sync.days-back:90}")
  private int daysBack;

  /**
   * 목록 동기화 → 상세 동기화를 한 번 실행한다.
   *
   * @return 건수 요약. 이미 실행 중이면 IpoSyncBusyException, 키가 없으면 OpenDartKeyMissingException
   */
  public IpoManualSyncResult syncNow() {
    if (openDartProperties.isApiKeyMissing()) {
      throw new OpenDartKeyMissingException();
    }
    return ipoSyncService.runExclusively(this::run);
  }

  private IpoManualSyncResult run() {
    IpoSyncResult r = ipoSyncService.sync(daysBack);
    // 목록 단계가 인증·요청제한 등으로 중단됐으면 상세 단계도 같은 이유로 실패하므로 건너뛴다
    int detail = r.isAborted() ? 0 : ipoDetailSyncService.syncRecent();
    log.info(
        "수동 IPO 동기화 완료: created={}, updated={}, failures={}, detail={}, aborted={}",
        r.getCreated(),
        r.getUpdated(),
        r.getFailureCount(),
        detail,
        r.isAborted());
    return new IpoManualSyncResult(
        r.getCreated(),
        r.getUpdated(),
        r.getSkippedManual() + r.getSkippedNonIpo(),
        r.getFailureCount(),
        r.getScheduleMerged(),
        detail,
        r.isAborted());
  }
}
