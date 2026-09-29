package com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm;

import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecuritiesFirmRepository extends JpaRepository<SecuritiesFirm, Long> {

  Optional<SecuritiesFirm> findByNameAndStatus(String name, String status);

  List<SecuritiesFirm> findByDeletedAtIsNull();

  Optional<SecuritiesFirm> findByIdAndDeletedAtIsNull(Long id);
}
