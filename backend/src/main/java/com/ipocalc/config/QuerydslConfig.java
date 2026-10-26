package com.ipocalc.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** QueryDSL JPA 조회 팩토리 설정. */
@Configuration
public class QuerydslConfig {
  /** 현재 트랜잭션의 영속성 컨텍스트를 사용하는 QueryDSL 조회 팩토리를 만든다. */
  @Bean
  public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
    return new JPAQueryFactory(entityManager);
  }
}
