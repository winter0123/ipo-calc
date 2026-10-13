package com.vibeaiteam.ipocatalog.repository.ipo;

import com.vibeaiteam.ipocatalog.domain.model.ipo.IpoDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 공모주 상세 정보 JPA 저장소(PK = 공모주 ID). */
@Repository
public interface IpoDetailRepository extends JpaRepository<IpoDetail, Long> {}
