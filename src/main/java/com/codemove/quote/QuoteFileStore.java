package com.codemove.quote;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 견적서 등록 원본 파일 (SCR-A-003/004, git 제외 폴더).
 * 첨부하면 {uploadDir}/quotes/drafts/{UUID}.{확장자} 에 두고, 등록하면 {uploadDir}/quotes/{UUID}/{원래 파일 이름} 으로 옮긴다.
 * DOCUMENT_PATH 에는 옮긴 경로를 둔다 (파일 이름이 곧 화면의 원본 파일 이름).
 */
@Component
public class QuoteFileStore {

	static final Set<String> PHOTO = Set.of("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp");
	static final String PDF = "pdf";
	private static final long MAX_BYTES = 20L * 1024 * 1024;

	private final Path root;

	public QuoteFileStore(@Value("${codemove.quote.upload-dir:uploads}") String uploadDir) {
		this.root = Path.of(uploadDir, "quotes");
	}

	/** 첨부한 파일 (등록 전) */
	public record Attached(String source, Path path, String fileName) {
	}

	/** 사진 · PDF 만 받는다. source: photo · pdf. 받을 수 없는 파일이면 IllegalArgumentException(화면 문구) */
	public Attached attach(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("파일을 다시 선택해 주세요.");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new IllegalArgumentException("파일은 20MB까지 첨부할 수 있어요.");
		}
		String ext = extension(file.getOriginalFilename());
		String source = PDF.equals(ext) ? "pdf" : PHOTO.contains(ext) ? "photo" : null;
		if (source == null) {
			throw new IllegalArgumentException("사진이나 PDF 파일만 첨부할 수 있어요.");
		}
		Path target = root.resolve("drafts").resolve(UUID.randomUUID() + "." + ext);
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("견적서 원본 저장 실패: " + target, e);
		}
		return new Attached(source, target, safeName(file.getOriginalFilename(), ext));
	}

	/** 등록할 때 원본을 둘 자리 (아직 옮기지 않음) */
	public Path target(String fileName) {
		return root.resolve(UUID.randomUUID().toString()).resolve(fileName);
	}

	/** 첨부 파일을 등록 자리로 옮긴다 */
	public void move(Path from, Path to) {
		try {
			Files.createDirectories(to.getParent());
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("견적서 원본 이동 실패: " + from, e);
		}
	}

	/** 파일과 (등록 폴더면) 빈 폴더까지 지운다. 실패해도 화면 동작은 계속한다 */
	public void delete(String path) {
		if (path == null) {
			return;
		}
		try {
			Path p = Path.of(path);
			Files.deleteIfExists(p);
			Path dir = p.getParent();
			if (dir != null && !dir.getFileName().toString().equals("drafts") && dir.startsWith(root)) {
				try (var left = Files.list(dir)) {
					if (left.findAny().isEmpty()) {
						Files.delete(dir);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// 파일 정리는 다음 기회에 (DB 기록과 화면에는 영향 없음)
		}
	}

	/** 확장자 소문자 */
	public static String extension(String name) {
		if (name == null || name.lastIndexOf('.') < 0) {
			return "";
		}
		return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	/** 화면에 보일 원본 파일 이름: 경로·파일 이름에 쓸 수 없는 글자를 지우고 100자까지 */
	static String safeName(String original, String ext) {
		String name = original == null ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[:*?\"<>|\\p{Cntrl}]", "_").trim();
		if (name.isEmpty() || name.startsWith(".")) {
			name = "견적서." + ext;
		}
		return name.length() > 100 ? name.substring(name.length() - 100) : name;
	}
}
