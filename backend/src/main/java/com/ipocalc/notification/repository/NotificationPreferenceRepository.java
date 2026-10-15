package com.ipocalc.notification.repository;

import com.ipocalc.notification.domain.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** 알림 종류 설정 저장소. */
public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, Long> {}
