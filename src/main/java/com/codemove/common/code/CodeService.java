package com.codemove.common.code;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.codemove.domain.Code;

import lombok.RequiredArgsConstructor;

/**
 * 공통코드 조회. 처음 쓸 때 전체를 읽어 메모리에 두고, 코드가 바뀌면 reload() 한다.
 * 템플릿에서: ${@codeService.name('HOUSE_TYPE', request.houseType)}
 */
@Service("codeService")
@RequiredArgsConstructor
public class CodeService {

	private final CodeMapper codeMapper;

	private volatile Map<String, List<Code>> cache;

	/** 그룹의 코드 목록 (정렬 순) */
	public List<Code> list(String groupCode) {
		return codes().getOrDefault(groupCode, List.of());
	}

	/** 관리자 표시명 (CODE_NAME). 없는 코드면 null */
	public String name(String groupCode, String code) {
		Code found = find(groupCode, code);
		return found != null ? found.getCodeName() : null;
	}

	/** 사용자앱 표시명 (USER_NAME, 없으면 CODE_NAME). 없는 코드면 null */
	public String userName(String groupCode, String code) {
		Code found = find(groupCode, code);
		return found != null ? found.getDisplayName() : null;
	}

	public void reload() {
		cache = null;
	}

	private Code find(String groupCode, String code) {
		if (code == null) {
			return null;
		}
		return list(groupCode).stream().filter(c -> c.getCode().equals(code)).findFirst().orElse(null);
	}

	private Map<String, List<Code>> codes() {
		Map<String, List<Code>> loaded = cache;
		if (loaded == null) {
			loaded = codeMapper.selectActiveCodes().stream()
					.collect(Collectors.groupingBy(Code::getGroupCode, LinkedHashMap::new, Collectors.toUnmodifiableList()));
			cache = loaded;
		}
		return loaded;
	}
}
