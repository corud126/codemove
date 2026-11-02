package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.codemove.common.mapper.HealthMapper;

@SpringBootTest
class DbConnectionTest {

	@Autowired
	private HealthMapper healthMapper;

	@Test
	void connectOracle() {
		String version = healthMapper.selectDbVersion();
		String user = healthMapper.selectCurrentUser();

		System.out.println("[DB] version   : " + version);
		System.out.println("[DB] user      : " + user);
		System.out.println("[DB] tables    : " + healthMapper.selectTableNames());
		System.out.println("[DB] sequences : " + healthMapper.selectSequenceNames());

		assertThat(version).contains("Oracle");
		assertThat(user).isNotBlank();
	}
}
