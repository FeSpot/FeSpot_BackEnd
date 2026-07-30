package com.api.fespot.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalLikeRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(
        named = "FESPOT_MYSQL_TEST",
        matches = "true"
)
class FestivalLikeMysqlIntegrationTest {

    private static final int YEAR = 2099;

    @Autowired
    private FestivalService festivalService;

    @Autowired
    private FestivalLikeService festivalLikeService;

    @Autowired
    private FestivalJpaRepository festivalJpaRepository;

    @Autowired
    private FestivalLikeRepository festivalLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private FestivalRepository festivalRepository;

    @Test
    void registersFestivalAndPersistsAndDeletesLike() {
        String contentId = Long.toUnsignedString(
                UUID.randomUUID().getMostSignificantBits());
        given(festivalRepository.findByYear(YEAR))
                .willReturn(List.of(festival(contentId)));

        festivalService.getHomeFestivals(YEAR);
        festivalService.getHomeFestivals(YEAR);

        Festival festival = festivalJpaRepository
                .findByContentIdAndYear(contentId, YEAR)
                .orElseThrow();
        User user = userRepository.save(User.create(
                "mysql-like-" + UUID.randomUUID() + "@example.com",
                "password-hash"));

        festivalLikeService.updateLike(
                user.getId(),
                contentId,
                YEAR,
                true);
        festivalLikeService.updateLike(
                user.getId(),
                contentId,
                YEAR,
                true);
        entityManager.flush();

        assertThat(festivalLikeRepository.findByUserAndFestival(user, festival))
                .isPresent();

        festivalLikeService.updateLike(
                user.getId(),
                contentId,
                YEAR,
                false);
        entityManager.flush();

        assertThat(festivalLikeRepository.findByUserAndFestival(user, festival))
                .isEmpty();
    }

    private FestivalSummaryReadModel festival(String contentId) {
        return new FestivalSummaryReadModel(
                contentId,
                "부산바다축제",
                "부산광역시 사하구",
                LocalDate.of(YEAR, 8, 1),
                LocalDate.of(YEAR, 8, 3),
                "https://example.com/festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.EPOCH);
    }
}
