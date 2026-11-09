package com.codemove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling: 예약 이사 완료 자동 처리 (ReservationService.completeOverdue) */
@SpringBootApplication
@EnableScheduling
public class CodemoveApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodemoveApplication.class, args);
	}

}
