package com.ipocalc.announcements.repository;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.announcements.domain.Announcement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 공지사항 데이터 저장소. */
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
  /** 삭제되지 않은 공지를 시작일 순서로 조회한다. */
  Page<Announcement> findAllByDeletedAtIsNullOrderByStartAtAscIdAsc(Pageable pageable);

  /** 상태별 삭제되지 않은 공지를 시작일 순서로 조회한다. */
  Page<Announcement> findAllByStatusAndDeletedAtIsNullOrderByStartAtAscIdAsc(
      RecordStatus status, Pageable pageable);

  /** 삭제되지 않은 공지를 ID로 조회한다. */
  Optional<Announcement> findByIdAndDeletedAtIsNull(Long id);

  /** 지정 기간에 게시 중인 상태별 공지 페이지를 조회한다. */
  @Query(
      "SELECT a FROM Announcement a WHERE a.status = :status AND a.deletedAt IS NULL "
          + "AND a.startAt <= :today AND a.endAt >= :today ORDER BY a.startAt ASC, a.id ASC")
  Page<Announcement>
      findActive(
          @Param("status") RecordStatus status,
          @Param("today") LocalDate today,
          Pageable pageable);

  /** 지정 기간에 게시 중인 상태별 공지 목록을 조회한다. */
  @Query(
      "SELECT a FROM Announcement a WHERE a.status = :status AND a.deletedAt IS NULL "
          + "AND a.startAt <= :today AND a.endAt >= :today ORDER BY a.startAt ASC, a.id ASC")
  List<Announcement>
      findActive(
          @Param("status") RecordStatus status,
          @Param("today") LocalDate today);

  /** 지정 기간에 게시 중인 공지 하나를 조회한다. */
  @Query(
      "SELECT a FROM Announcement a WHERE a.id = :id AND a.status = :status "
          + "AND a.deletedAt IS NULL AND a.startAt <= :today AND a.endAt >= :today")
  Optional<Announcement>
      findActiveById(
          @Param("id") Long id,
          @Param("status") RecordStatus status,
          @Param("today") LocalDate today);
}
