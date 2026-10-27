package com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.IpoCalcApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** V25: 테이블·컬럼 한글 설명(COMMENT)이 DB에 붙었는지 확인한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class KoreanCommentsMigrationTest {

  @PersistenceContext private EntityManager entityManager;

  private String columnComment(String table, String column) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT col_description(CAST(:t AS regclass), a.attnum) FROM pg_attribute a "
                    + "WHERE a.attrelid = CAST(:t AS regclass) AND a.attname = :c")
            .setParameter("t", table)
            .setParameter("c", column)
            .getSingleResult();
  }

  @Test
  void 컬럼_설명이_붙는다() {
    assertThat(columnComment("sales", "fee")).contains("2026-10-02");
    assertThat(columnComment("ipos", "listing_date")).contains("상장일");
    assertThat(columnComment("accounts", "role")).contains("ADMIN");
  }

  @Test
  void 테이블_설명이_붙는다() {
    Object comment =
        entityManager
            .createNativeQuery("SELECT obj_description(CAST('ipos' AS regclass), 'pg_class')")
            .getSingleResult();
    assertThat((String) comment).contains("공모주");
  }
}
