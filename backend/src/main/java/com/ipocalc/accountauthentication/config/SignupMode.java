package com.ipocalc.accountauthentication.config;

/** 가입 허용 방식. */
public enum SignupMode {
  /** 누구나 가입하고 바로 로그인한다. */
  OPEN,
  /** 가입은 받되 관리자가 승인하기 전에는 로그인할 수 없다(비공개 운영 기본값). */
  APPROVAL,
  /** 가입을 받지 않는다. */
  CLOSED
}
