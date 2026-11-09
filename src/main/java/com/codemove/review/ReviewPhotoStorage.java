package com.codemove.review;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 후기 사진 저장: {uploadDir}/{UUID}.{확장자} (기본 uploads/reviews, git 제외) */
@Component
public class ReviewPhotoStorage {

	private final Path dir;

	public ReviewPhotoStorage(@Value("${codemove.review.upload-dir:uploads/reviews}") String uploadDir) {
		this.dir = Path.of(uploadDir);
	}

	public Path save(InputStream in, String originalName) throws IOException {
		Files.createDirectories(dir);
		String ext = StringUtils.getFilenameExtension(originalName);
		String safeExt = ext != null && ext.matches("[A-Za-z0-9]{1,5}") ? "." + ext.toLowerCase() : "";
		Path target = dir.resolve(UUID.randomUUID() + safeExt);
		Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	/** 후기 등록이 실패하면 먼저 저장한 사진 정리 */
	public void delete(String path) {
		try {
			Files.deleteIfExists(Path.of(path));
		} catch (IOException ignored) {
			// 지우지 못해도 후기 흐름에는 영향 없음
		}
	}
}
