package com.codemove.web;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

/** 저장된 파일 내려주기. 경로 대신 인터넷 주소(http·https)가 저장돼 있으면 그 주소로 보낸다 (기존 예시 데이터) */
public final class FileResponses {

	private FileResponses() {
	}

	/** path 가 없거나 파일이 없으면 404 */
	public static ResponseEntity<Resource> serve(String path) {
		if (path == null || path.isBlank()) {
			return ResponseEntity.notFound().build();
		}
		if (path.startsWith("http://") || path.startsWith("https://")) {
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build();
		}
		try {
			if (!Files.exists(Path.of(path))) {
				return ResponseEntity.notFound().build();
			}
		} catch (InvalidPathException e) {
			return ResponseEntity.notFound().build();
		}
		MediaType type = MediaTypeFactory.getMediaType(path).orElse(MediaType.APPLICATION_OCTET_STREAM);
		return ResponseEntity.ok().contentType(type).body(new FileSystemResource(path));
	}
}
