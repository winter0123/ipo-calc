package com.ipocalc;

import com.ipocalc.allocationmanagement.service.allocation.ActiveAllocationReaderAdapter;
import com.ipocalc.config.QuerydslConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** IPO Calc 백엔드 애플리케이션. */
@SpringBootApplication
@Import({QuerydslConfig.class, ActiveAllocationReaderAdapter.class})
public class IpoCalcApplication {

  /** 애플리케이션을 기동한다. */
  public static void main(String[] args) {
    SpringApplication.run(IpoCalcApplication.class, args);
  }
}
