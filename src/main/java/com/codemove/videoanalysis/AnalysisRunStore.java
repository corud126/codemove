package com.codemove.videoanalysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실행 기록 저장소: runDir/runs/{id}.json · 테스트 영상: runDir/videos/{id}.{확장자}.
 * 검증 단계라 DB 대신 파일에 둔다 (uploads/ 는 Git 에 올리지 않는다). 삭제하면 기록과 영상을 함께 지운다.
 */
@Slf4j
@Component
public class AnalysisRunStore {

	private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

	private final Path runs;
	private final Path videos;

	public AnalysisRunStore(VideoAnalysisProperties properties) {
		Path base = Path.of(properties.runDir());
		this.runs = base.resolve("runs");
		this.videos = base.resolve("videos");
	}

	public Path videoDir() {
		try {
			return Files.createDirectories(videos);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public Path videoPath(String storedName) {
		return videos.resolve(storedName);
	}

	public void save(AnalysisRun run) {
		try {
			Files.createDirectories(runs);
			Files.writeString(runs.resolve(run.id() + ".json"), JSON.writeValueAsString(run), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public AnalysisRun find(String id) {
		if (!safe(id)) {
			return null;
		}
		Path file = runs.resolve(id + ".json");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), AnalysisRun.class);
		} catch (IOException | JacksonException e) {
			log.warn("실행 기록 읽기 실패: {}", file, e);
			return null;
		}
	}

	/** 최근 순 limit 개 */
	public List<AnalysisRun> recent(int limit) {
		if (!Files.isDirectory(runs)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(runs)) {
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5)).sorted(Comparator.reverseOrder()).limit(limit)
					.map(this::find).filter(Objects::nonNull).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 기록과 영상을 지운다. 없으면 false */
	public boolean delete(String id) {
		AnalysisRun run = find(id);
		if (run == null) {
			return false;
		}
		try {
			if (run.video() != null && run.video().storedName() != null) {
				Files.deleteIfExists(videos.resolve(run.video().storedName()));
			}
			return Files.deleteIfExists(runs.resolve(id + ".json"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 경로 조작 방지: 실행 ID 는 숫자 · 하이픈만 */
	static boolean safe(String id) {
		return id != null && id.matches("[0-9-]{10,40}");
	}
}
