package com.vibeaiteam.ipocatalog.service.ipo.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 증권 영업일 계산 유틸리티.
 *
 * <p>한국거래소(KRX) 휴장일을 제외한 평일을 영업일로 계산한다. 휴장일은 클래스패스 리소스 {@code krx-holidays.txt}에서 로드하며, 형식은 한 줄에
 * {@code yyyy-MM-dd}, {@code #}으로 시작하는 줄은 주석으로 처리한다. 리소스에 없는 연도의 날짜는 주말(토/일)만 제외하고 계산하되 WARN 로그를
 * 남긴다.
 */
@Component
public class BusinessDayCalculator {

  private static final Logger log = LoggerFactory.getLogger(BusinessDayCalculator.class);
  private static final String RESOURCE_PATH = "krx-holidays.txt";

  private final Set<LocalDate> holidays = new HashSet<>();
  private int minYear = Integer.MAX_VALUE;
  private int maxYear = Integer.MIN_VALUE;
  private boolean initialized = false;

  /** 주어진 날짜의 다음 영업일을 반환한다 (주말 및 KRX 휴장일 제외). */
  public LocalDate nextBusinessDay(LocalDate date) {
    initialize();
    warnIfYearOutOfRange(date);
    LocalDate next = date.plusDays(1);
    while (!isBusinessDay(next)) {
      next = next.plusDays(1);
    }
    return next;
  }

  /** 주어진 날짜가 영업일인지 판별한다 (주말 및 KRX 휴장일 제외). */
  public boolean isBusinessDay(LocalDate date) {
    initialize();
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return false;
    }
    return !holidays.contains(date);
  }

  private synchronized void initialize() {
    if (initialized) {
      return;
    }
    try {
      ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
      if (!resource.exists()) {
        log.warn("KRX 휴장일 리소스 파일을 찾을 수 없습니다: {}", RESOURCE_PATH);
        initialized = true;
        return;
      }
      try (InputStream is = resource.getInputStream();
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          try {
            LocalDate holiday = LocalDate.parse(line);
            holidays.add(holiday);
            int year = holiday.getYear();
            minYear = Math.min(minYear, year);
            maxYear = Math.max(maxYear, year);
          } catch (Exception e) {
            log.warn("KRX 휴장일 파싱 실패 (줄 무시): {}", line);
          }
        }
      }
      log.info("KRX 휴장일 {}건 로드 완료 (연도 범위: {}-{})", holidays.size(), minYear, maxYear);
    } catch (IOException e) {
      log.warn("KRX 휴장일 리소스 로드 실패: {}", e.getMessage());
    }
    initialized = true;
  }

  /** 리소스에 포함된 연도 범위 밖의 날짜인지 확인하여 WARN 로그를 남긴다. */
  private void warnIfYearOutOfRange(LocalDate date) {
    int year = date.getYear();
    if (year < minYear || year > maxYear) {
      log.warn("KRX 휴장일 리소스에 없는 연도({})의 날짜입니다. 주말만 제외한 영업일 계산이 수행됩니다. 휴장일 목록 갱신이 필요합니다.", year);
    }
  }
}
