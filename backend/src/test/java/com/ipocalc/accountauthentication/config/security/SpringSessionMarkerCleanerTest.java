package com.ipocalc.accountauthentication.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

/** Spring Session 자체를 유지하면서 임퍼소네이션 마커만 정리하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class SpringSessionMarkerCleanerTest {

  private static final String MARKER = "IMPERSONATION_SESSION_ID";

  @Mock
  private ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositoryProvider;

  @Mock private FindByIndexNameSessionRepository<Session> sessionRepository;

  @InjectMocks private SpringSessionMarkerCleaner cleaner;

  @Test
  void removesOnlyMarkerAndSavesExistingSpringSession() {
    MapSession session = new MapSession("spring-session-id");
    session.setAttribute(MARKER, "impersonation-uuid");
    session.setAttribute("other", "preserved");
    doReturn(sessionRepository).when(repositoryProvider).getIfAvailable();
    when(sessionRepository.findById("spring-session-id")).thenReturn(session);

    cleaner.removeMarker("spring-session-id");

    assertThat((String) session.getAttribute(MARKER)).isNull();
    assertThat((String) session.getAttribute("other")).isEqualTo("preserved");
    verify(sessionRepository).save(session);
    verify(sessionRepository, never()).deleteById("spring-session-id");
  }

  @Test
  void missingSpringSessionRepositoryFailsExplicitly() {
    doReturn(null).when(repositoryProvider).getIfAvailable();

    assertThatThrownBy(() -> cleaner.removeMarker("spring-session-id"))
        .isInstanceOf(IllegalStateException.class);
  }
}
