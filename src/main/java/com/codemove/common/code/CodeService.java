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

	/**
	 * 화면 표시명으로 코드 찾기. 사용자앱 표시명과 같거나 그 앞부분인 경우('원룸' → '원룸 / 오피스텔'), 또는 관리자 표시명과 같은 경우.
	 * 없으면 null
	 */
	public String codeOf(String groupCode, String label) {
		if (label == null || label.isBlank()) {
			return null;
		}
		return list(groupCode).stream()
				.filter(c -> c.getDisplayName().equals(label) || c.getDisplayName().startsWith(label + " ")
						|| c.getCodeName().equals(label))
				.map(Code::getCode).findFirst().orElse(null);
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
