package com.api.fespot.domain.interest.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.interest.exception.InterestErrorCode;
import com.api.fespot.domain.interest.exception.InterestFestivalAlreadyExistsException;
import com.api.fespot.domain.interest.repository.InterestFestivalRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.aop.WrapDataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(AopAutoConfiguration.class)   // @DataJpaTest는 @Aspect를 적용하지 않으므로 직접 켠다
@Import({InterestFestivalService.class, WrapDataIntegrityViolationException.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 각 스레드가 자기 트랜잭션을 커밋해야 하므로 테스트 트랜잭션을 끈다
@Testcontainers(disabledWithoutDocker = true)
public class InterestFestivalServiceConcurrencyTest {

    private static final int THREAD_COUNT = 2;
    private static final int YEAR = 2026;
    private static final String CONTENT_ID = "1";

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private InterestFestivalService interestFestivalService;
    @Autowired private FestivalJpaRepository festivalJpaRepository;
    @Autowired private UserRepository userRepository;

    @MockitoSpyBean private InterestFestivalRepository interestFestivalRepository;

    // 관심 축제 목록 조회에서만 쓰는 KTO OpenAPI 조회 포트라 목으로 대체한다.
    @MockitoBean private FestivalRepository festivalRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("interest-concurrency-test@fespot.com", "tester"));
        festivalJpaRepository.save(Festival.create(CONTENT_ID, YEAR));
    }

    // 테스트 트랜잭션이 없어 저장 내용이 커밋되므로 테스트마다 직접 정리한다.
    @AfterEach
    void tearDown() {
        interestFestivalRepository.deleteAll();
        festivalJpaRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 축제에 대한 관심 등록 요청이 동시에 도달하면 하나만 저장되고, 나머지는 InterestFestivalAlreadyExistsException(409)으로 변환된다.")
    void testConcurrentSaveInterestFestival() throws InterruptedException {
        // given
        // 한쪽이 먼저 커밋하면 사전 중복 검사에서 걸러져 unique 제약까지 가지 않으므로,
        // 두 요청이 모두 중복 검사를 통과한 뒤에 INSERT하도록 맞춘다. (아직 저장된 관심 축제는 없다)
        CountDownLatch duplicateCheckLatch = new CountDownLatch(THREAD_COUNT);
        willAnswer(invocation -> {
            duplicateCheckLatch.countDown();
            duplicateCheckLatch.await(5, TimeUnit.SECONDS);
            return false;
        }).given(interestFestivalRepository).existsByUserAndFestival(any(User.class), any(Festival.class));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    interestFestivalService.saveInterestFestival(user.getId(), YEAR, CONTENT_ID);
                    successCount.incrementAndGet();
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failures).singleElement()
                .isInstanceOfSatisfying(InterestFestivalAlreadyExistsException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(InterestErrorCode.INTEREST_CONFLICT_409));
        assertThat(interestFestivalRepository.count()).isEqualTo(1);
    }
}
