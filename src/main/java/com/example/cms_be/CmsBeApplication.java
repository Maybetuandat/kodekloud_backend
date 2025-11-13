package com.example.cms_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CmsBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CmsBeApplication.class, args);
	}

}
