package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import java.util.List;
import java.util.Optional;

/**
 * 축제 정보를 영속화하지 않고 OpenAPI에서 읽는 조회
 */
public interface FestivalRepository {

    List<FestivalSummaryReadModel> findByYear(int year);

    Optional<FestivalDetailReadModel> findByContentId(String contentId);
}
