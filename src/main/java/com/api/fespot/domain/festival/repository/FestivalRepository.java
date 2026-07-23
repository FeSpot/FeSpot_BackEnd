package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.FestivalReadModel;
import java.util.List;

/**
 * 축제 정보를 영속화하지 않고 OpenAPI에서 읽는 조회
 */
public interface FestivalRepository {

    List<FestivalReadModel> findByYear(int year);
}
