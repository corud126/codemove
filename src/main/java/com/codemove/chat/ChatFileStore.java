package com.codemove.chat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 채팅 첨부 파일 저장 (uploads/chat/{ROOM_ID}/{UUID}.{확장자}, git 제외 폴더).
 * DB(ATTACHMENT_PATH)에는 업로드 폴더 기준 상대 경로를 둔다.
 */
@Component
public class ChatFileStore {

	/** 사진 · 문서만 받는다 */
	static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp",
			"pdf", "hwp", "hwpx", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip");

	private final Path root;
	private final long maxBytes;

	public ChatFileStore(@Value("${codemove.chat.upload-dir:uploads/chat}") String dir,
			@Value("${codemove.chat.max-file-mb:10}") long maxMb) {
		this.root = Paths.get(dir).toAbsolutePath().normalize();
		this.maxBytes = maxMb * 1024 * 1024;
	}

	public long maxMb() {
		return maxBytes / 1024 / 1024;
	}

	/** 저장하고 상대 경로를 돌려준다. 받을 수 없는 파일이면 IllegalArgumentException(화면 문구) */
	public String save(Long roomId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("파일을 다시 선택해 주세요.");
		}
		if (file.getSize() > maxBytes) {
			throw new IllegalArgumentException("파일은 " + maxMb() + "MB까지 보낼 수 있어요.");
		}
		String ext = extension(file.getOriginalFilename());
		if (!ALLOWED.contains(ext)) {
			throw new IllegalArgumentException("사진이나 문서 파일만 보낼 수 있어요.");
		}
		String relative = roomId + "/" + UUID.randomUUID() + "." + ext;
		Path target = resolve(relative);
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("채팅 첨부 저장 실패: " + relative, e);
		}
		return relative;
	}

	/** 저장된 파일 위치 (업로드 폴더 밖이면 null) */
	public Path resolve(String relative) {
		Path p = root.resolve(relative).normalize();
		return p.startsWith(root) ? p : null;
	}

	/** 화면에 보일 원본 파일명 (경로 제거, 100자) */
	public static String displayName(String original) {
		String name = original == null ? "" : original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).trim();
		if (name.isEmpty()) {
			name = "첨부파일";
		}
		return name.length() > 100 ? name.substring(name.length() - 100) : name;
	}

	private static String extension(String name) {
		if (name == null || name.lastIndexOf('.') < 0) {
			return "";
		}
		return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}
}
