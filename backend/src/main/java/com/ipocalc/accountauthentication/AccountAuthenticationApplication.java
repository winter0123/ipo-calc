package com.ipocalc.accountauthentication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 계정 JPA 엔티티. */
@SpringBootApplication
@ComponentScan(basePackages = "com.ipocalc")
@EntityScan(basePackages = "com.ipocalc")
@EnableJpaRepositories(basePackages = "com.ipocalc")
public class AccountAuthenticationApplication {

  /** 애플리케이션을 기동한다. */
  public static void main(String[] args) {
    SpringApplication.run(AccountAuthenticationApplication.class, args);
  }
}
