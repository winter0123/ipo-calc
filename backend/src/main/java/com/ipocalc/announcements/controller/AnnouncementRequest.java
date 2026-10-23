package com.ipocalc.announcements.controller;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import java.time.LocalDate;

/** 공지사항 생성·수정 요청. */
public record AnnouncementRequest(
    String title, String content, LocalDate startAt, LocalDate endAt, RecordStatus status) {}
