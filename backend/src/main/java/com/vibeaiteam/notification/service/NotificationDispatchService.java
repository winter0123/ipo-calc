package com.vibeaiteam.notification.service;

import com.vibeaiteam.notification.repository.NotificationTargetRepository;
import com.vibeaiteam.notification.repository.NotificationTargetRepository.Target;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** 오늘 보낼 알림(청약 마감, 상장일)을 골라 보낸다. 같은 날 같은 대상에는 한 번만 보낸다. */
@Service
public class NotificationDispatchService {

  static final String DEADLINE = "SUBSCRIPTION_DEADLINE";
  static final String LISTING = "LISTING_DAY";

  private final NotificationTargetRepository targets;
  private final PushDeliveryService delivery;

  /** 의존성을 주입받는다. */
  public NotificationDispatchService(
      NotificationTargetRepository targets, PushDeliveryService delivery) {
    this.targets = targets;
    this.delivery = delivery;
  }

  /** 오늘 날짜 기준 알림을 모두 보내고 보낸 (계정, 대상) 건수를 돌려준다. */
  public int dispatch(LocalDate today) {
    return send(
            targets.findSubscriptionDeadlineTargets(today),
            DEADLINE,
            today,
            "청약 마감 오늘",
            "오늘 청약이 마감됩니다",
            "/ipos/")
        + send(
            targets.findListingDayTargets(today),
            LISTING,
            today,
            "상장일 오늘",
            "오늘 상장합니다. 보유 수량을 확인하세요",
            "/investments");
  }

  private int send(
      List<Target> list, String type, LocalDate today, String title, String body, String url) {
    int sent = 0;
    for (Target t : list) {
      if (targets.wasSent(t.accountId(), type, t.ipoId(), today)) {
        continue;
      }
      String link = url.endsWith("/") ? url + t.ipoId() : url;
      // 기기 중 하나라도 받았을 때만 기록해, 전부 실패한 날은 다음 실행에서 다시 시도한다
      if (delivery.deliver(
              t.accountId(), title + ": " + t.ipoName(), body, link, type + "-" + t.ipoId())
          > 0) {
        targets.markSent(t.accountId(), type, t.ipoId(), today);
        sent++;
      }
    }
    return sent;
  }
}
