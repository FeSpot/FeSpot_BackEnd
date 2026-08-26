package com.api.fespot.domain.auth.repository.jpa;

import com.api.fespot.domain.auth.entity.Credential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    /**
     * 사용자의 인증 정보를 타입(로컬/소셜) 구분 없이 조회한다.
     * JOINED 상속이므로 엔티티 삭제 시 하위 타입 행까지 함께 제거된다.
     *
     * @param userId 사용자 ID
     * @return 사용자의 인증 정보
     */
    Optional<Credential> findByUser_Id(Long userId);
}
