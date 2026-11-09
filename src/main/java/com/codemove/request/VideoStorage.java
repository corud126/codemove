package com.codemove.request;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 견적 요청 영상 저장: {uploadDir}/videos/{UUID}.{확장자} (원래 파일 이름은 DB 에만 둔다) */
@Component
public class VideoStorage {

	private final Path dir;

	public VideoStorage(QuoteProperties props) {
		this.dir = Path.of(props.uploadDir(), "videos");
	}

	public Path save(InputStream in, String originalName) throws IOException {
		Files.createDirectories(dir);
		String ext = StringUtils.getFilenameExtension(originalName);
		String safeExt = ext != null && ext.matches("[A-Za-z0-9]{1,5}") ? "." + ext.toLowerCase() : "";
		Path target = dir.resolve(UUID.randomUUID() + safeExt);
		Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	/** 요청을 내지 않고 다른 영상으로 바꾼 경우 이전 파일 정리 */
	public void delete(String path) {
		try {
			Files.deleteIfExists(Path.of(path));
		} catch (IOException ignored) {
			// 지우지 못해도 요청 흐름에는 영향 없음
		}
	}
}
