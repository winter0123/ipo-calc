package com.ipocalc.notification.service;

import com.ipocalc.notification.domain.model.PushSubscription;

/** 푸시 한 건을 브라우저 푸시 서비스로 보내는 포트. 테스트에서는 mock으로 바꾼다. */
public interface PushSender {

  /** 발송 결과. */
  enum Result {
    /** 푸시 서비스가 받아 갔다. */
    OK,
    /** 404/410: 구독이 만료·해지됐다. 구독을 정리해야 한다. */
    GONE,
    /** 그 밖의 실패(네트워크·일시 오류). 구독은 유지한다. */
    FAILED
  }

  /** 암호화된 payload(JSON 바이트)를 구독 기기로 보낸다. */
  Result send(PushSubscription subscription, byte[] payload);
}
