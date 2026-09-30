package com.vibeaiteam.ipocatalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정.
 *
 * <p>@Scheduled 어노테이션을 사용하는 빈(예: {@link
 * com.vibeaiteam.ipocatalog.service.holiday.MarketHolidaySyncScheduler}) 이 동작하려면 이 설정이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
