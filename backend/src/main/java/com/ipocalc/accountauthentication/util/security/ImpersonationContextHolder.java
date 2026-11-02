package com.ipocalc.accountauthentication.util.security;

/**
 * 임퍼소네이션 컨텍스트를 ThreadLocal로 관리하는 홀더. SecurityContext는 관리자 Authentication을 유지하고, 임퍼소네이션 대상 회원 정보는
 * 별도로 관리한다.
 */
public class ImpersonationContextHolder {

  private static final ThreadLocal<Long> ACTOR_ADMIN_ID = new ThreadLocal<>();
  private static final ThreadLocal<Long> SUBJECT_ACCOUNT_ID = new ThreadLocal<>();
  private static final ThreadLocal<String> IMPERSONATION_SESSION_ID = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> IS_IMPERSONATING = ThreadLocal.withInitial(() -> false);

  private ImpersonationContextHolder() {}

  /** 요청 스레드에 행위자, 대상 회원, 세션 정보를 설정한다. */
  public static void start(Long adminId, Long subjectId, String sessionId) {
    ACTOR_ADMIN_ID.set(adminId);
    SUBJECT_ACCOUNT_ID.set(subjectId);
    IMPERSONATION_SESSION_ID.set(sessionId);
    IS_IMPERSONATING.set(true);
  }

  /** 요청 스레드의 임퍼소네이션 정보를 제거한다. */
  public static void end() {
    ACTOR_ADMIN_ID.remove();
    SUBJECT_ACCOUNT_ID.remove();
    IMPERSONATION_SESSION_ID.remove();
    IS_IMPERSONATING.remove();
  }

  /** 행위자인 관리자 ID를 반환한다. */
  public static Long getActorAdminId() {
    return ACTOR_ADMIN_ID.get();
  }

  /** 대상 회원 ID를 반환한다. */
  public static Long getSubjectAccountId() {
    return SUBJECT_ACCOUNT_ID.get();
  }

  /** 임퍼소네이션 세션 ID를 반환한다. */
  public static String getImpersonationSessionId() {
    return IMPERSONATION_SESSION_ID.get();
  }

  /** 현재 요청이 임퍼소네이션 중인지 반환한다. */
  public static boolean isImpersonating() {
    return Boolean.TRUE.equals(IS_IMPERSONATING.get());
  }
}
