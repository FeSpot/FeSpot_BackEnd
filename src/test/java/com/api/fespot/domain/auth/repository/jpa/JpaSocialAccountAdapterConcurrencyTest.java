package com.api.fespot.domain.auth.repository.jpa;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.auth.repository.SocialAccountPort.SocialAccountRegistration;
import com.api.fespot.domain.auth.repository.SocialAccountPort.SocialIdentityRegistrationConflictException;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.aop.WrapDataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(AopAutoConfiguration.class)   // @DataJpaTest는 @Aspect를 적용하지 않으므로 직접 켠다
@Import({WrapDataIntegrityViolationException.class, JpaSocialAccountAdapterConcurrencyTest.AdapterConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 각 스레드가 자기 트랜잭션을 커밋해야 하므로 테스트 트랜잭션을 끈다
@Testcontainers(disabledWithoutDocker = true)
public class JpaSocialAccountAdapterConcurrencyTest {

    private static final int THREAD_COUNT = 2;

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private SocialAccountPort socialAccountPort;
    @Autowired private SocialCredentialRepository socialCredentialRepository;
    @Autowired private UserRepository userRepository;

    // 테스트 트랜잭션이 없어 저장 내용이 커밋되므로 테스트마다 직접 정리한다.
    @AfterEach
    void tearDown() {
        socialCredentialRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 소셜 계정의 가입 요청이 동시에 도달하면 하나만 등록되고, 나머지는 SocialIdentityRegistrationConflictException(409)으로 변환된다.")
    void testConcurrentRegisterWithSameSocialIdentity() throws InterruptedException {
        // given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            // 이메일 사전 검사(uk_users_email)에 걸리지 않도록 이메일만 다르게 해 소셜 계정 제약 충돌만 남긴다.
            SocialAccountRegistration registration = new SocialAccountRegistration(
                    OAuthProvider.KAKAO,
                    "12345",
                    "social-concurrency-test-" + i + "@fespot.com",
                    "tester"
            );
            executor.submit(() -> {
                try {
                    startLatch.await();
                    socialAccountPort.register(registration);
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
                .isInstanceOfSatisfying(SocialIdentityRegistrationConflictException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(AuthErrorResponseCode.DUPLICATED_SOCIAL_ACCOUNT_409));
        assertThat(socialCredentialRepository.count()).isEqualTo(1);
        // 충돌한 요청의 사용자 저장도 같은 트랜잭션에서 롤백된다.
        assertThat(userRepository.count()).isEqualTo(1);
    }

    // 운영에서는 SocialLoginConfig가 등록하지만, OAuth 클라이언트까지 끌고 오지 않도록 어댑터만 등록한다.
    @TestConfiguration
    static class AdapterConfig {

        @Bean
        SocialAccountPort socialAccountPort(
                UserRepository userRepository,
                SocialCredentialRepository socialCredentialRepository
        ) {
            return new JpaSocialAccountAdapter(userRepository, socialCredentialRepository);
        }
    }
}
