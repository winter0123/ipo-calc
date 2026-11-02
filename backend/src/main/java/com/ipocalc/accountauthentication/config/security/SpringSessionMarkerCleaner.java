package com.ipocalc.accountauthentication.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Spring Session을 삭제하지 않고 임퍼소네이션 마커 속성만 제거한다. */
@Component
@RequiredArgsConstructor
public class SpringSessionMarkerCleaner {

  private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>>
      repositoryProvider;

  /** 저장된 Spring Session에서 임퍼소네이션 마커만 제거하고 세션을 저장한다. */
  public void removeMarker(String springSessionId) {
    if (springSessionId == null || springSessionId.isBlank()) {
      return;
    }

    FindByIndexNameSessionRepository<? extends Session> repository =
        repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new IllegalStateException("Spring Session 저장소를 사용할 수 없습니다");
    }
    removeMarker(repository, springSessionId);
  }

  private <S extends Session> void removeMarker(
      FindByIndexNameSessionRepository<S> repository, String springSessionId) {
    S session = repository.findById(springSessionId);
    if (session != null && session.getAttribute("IMPERSONATION_SESSION_ID") != null) {
      session.removeAttribute("IMPERSONATION_SESSION_ID");
      repository.save(session);
    }
  }
}
