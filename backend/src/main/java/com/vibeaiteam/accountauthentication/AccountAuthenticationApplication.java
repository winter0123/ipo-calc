package com.vibeaiteam.accountauthentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 계정 JPA 엔티티.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.vibeaiteam")
@EntityScan(basePackages = "com.vibeaiteam")
@EnableJpaRepositories(basePackages = "com.vibeaiteam")
public class AccountAuthenticationApplication {

  /**
   * 애플리케이션을 기동한다.
   */
  public static void main(String[] args) {
    SpringApplication.run(AccountAuthenticationApplication.class, args);
  }
}
