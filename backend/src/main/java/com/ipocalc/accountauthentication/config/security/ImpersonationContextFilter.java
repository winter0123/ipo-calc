package com.ipocalc.accountauthentication.config.security;

import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.domain.model.account.AccountRole;
import com.ipocalc.accountauthentication.domain.model.impersonation.ImpersonationSession;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.accountauthentication.service.impersonation.ImpersonationService;
import com.ipocalc.accountauthentication.util.security.ImpersonationContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 HTTP 세션의 임퍼소네이션 마커를 검증하고 컨텍스트를 설정/해제한다.
 *
 * <p>SecurityContext는 관리자 Authentication을 그대로 유지한다. 마커가 가리키는 세션이 종료·만료됐거나, 행위자·대상 계정 상태가 바뀌었거나, 현재
 * 인증이 행위자 관리자가 아니면 마커만 제거하고 요청은 관리자 세션으로 계속 처리한다(세션 무효화 금지).
 */
public class ImpersonationContextFilter extends OncePerRequestFilter {

  private final ImpersonationService impersonationService;
  private final AccountRepository accountRepository;

  /** 의존성을 주입해 요청별 임퍼소네이션 검증 필터를 생성한다. */
  public ImpersonationContextFilter(
      ImpersonationService impersonationService, AccountRepository accountRepository) {
    this.impersonationService = impersonationService;
    this.accountRepository = accountRepository;
  }

  /**
   * 요청 컨텍스트를 구성하고 완료 후 제거한다.
   *
   * @param request HTTP 요청
   * @param response HTTP 응답
   * @param filterChain 다음 필터 체인
   * @throws ServletException 서블릿 처리 오류
   * @throws IOException 입출력 오류
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    String sessionId =
        session == null
            ? null
            : (String) session.getAttribute(ImpersonationService.SESSION_ATTRIBUTE);

    ImpersonationSession active = sessionId == null ? null : resolveActive(sessionId);
    if (sessionId != null && active == null) {
      session.removeAttribute(ImpersonationService.SESSION_ATTRIBUTE);
    }

    if (active != null && !isValidActor(active)) {
      session.removeAttribute(ImpersonationService.SESSION_ATTRIBUTE);
      active = null;
    }

    try {
      if (active != null) {
        ImpersonationContextHolder.start(
            active.getActorAdminId(), active.getSubjectAccountId(), active.getSessionId());
      }
      filterChain.doFilter(request, response);
    } finally {
      ImpersonationContextHolder.end();
    }
  }

  private ImpersonationSession resolveActive(String sessionId) {
    Optional<ImpersonationSession> found = impersonationService.findSessionById(sessionId);
    if (found.isEmpty()) {
      return null;
    }
    ImpersonationSession session = found.get();
    if (session.getEndedAt() != null) {
      return null;
    }
    if (session.isExpired()) {
      impersonationService.endImpersonation(sessionId, "EXPIRED");
      return null;
    }
    return session;
  }

  private boolean isValidActor(ImpersonationSession session) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    Account actor = accountRepository.findById(session.getActorAdminId()).orElse(null);
    if (actor == null
        || !actor.isApproved()
        || actor.getRole() != AccountRole.ADMIN
        || !actor.getEmail().equals(authentication.getName())) {
      return false;
    }
    return accountRepository.findByIdAndApprovedTrue(session.getSubjectAccountId()).isPresent();
  }
}
