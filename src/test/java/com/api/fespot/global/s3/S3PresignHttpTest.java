package com.api.fespot.global.s3;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/api"   // 운영과 동일한 컨텍스트 패스로 검증
)
@Testcontainers(disabledWithoutDocker = true)
public class S3PresignHttpTest {

    private static final String FILE_URL_PATTERN =
            "https://fespot-test-bucket\\.s3\\.ap-northeast-2\\.amazonaws\\.com/report/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg";

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    // JWT 필터의 액세스 토큰 블랙리스트 조회가 요청마다 Redis를 사용하므로 함께 띄운다.
    @Container
    @ServiceConnection
    private static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @LocalServerPort private int port;

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        User user = userRepository.save(User.create("s3-http-test@fespot.com", "passwordHash"));
        accessToken = jwtTokenProvider.generateAccessToken(user);
    }

    // RANDOM_PORT 방식은 서버 스레드에서 트랜잭션이 커밋되므로 테스트마다 직접 정리한다.
    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private Response createPresignedUrl(Map<String, Object> body) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/s3/presigned-url");
    }

    private Map<String, Object> validBody() {
        return Map.of(
                "category", "REPORT",
                "fileName", "photo.jpg",
                "contentType", "image/jpeg"
        );
    }

    @Test
    @DisplayName("presigned URL 발급 성공 시 201과 업로드 URL, 실제 파일 URL을 반환한다.")
    void createPresignedUrlReturns201WithUrls() {
        createPresignedUrl(validBody())
                .then()
                .statusCode(201)
                .body("isSuccess", equalTo(true))
                // presigned URL은 서명 쿼리 파라미터가 붙은 버킷 주소여야 한다
                .body("data.presignedUrl",
                        startsWith("https://fespot-test-bucket.s3.ap-northeast-2.amazonaws.com/report/"))
                .body("data.presignedUrl", containsString("X-Amz-Signature="))
                .body("data.fileUrl", matchesPattern(FILE_URL_PATTERN));
    }

    @Test
    @DisplayName("jpeg 확장자는 jpg로 정규화된 key로 발급된다.")
    void normalizesJpegExtensionToJpg() {
        createPresignedUrl(Map.of(
                "category", "REPORT",
                "fileName", "photo.jpeg",
                "contentType", "image/jpeg"
        ))
                .then()
                .statusCode(201)
                .body("data.fileUrl", matchesPattern(FILE_URL_PATTERN));
    }

    @Test
    @DisplayName("허용하지 않는 확장자면 400과 전용 에러 코드를 반환한다.")
    void returns400WhenExtensionIsNotAllowed() {
        createPresignedUrl(Map.of(
                "category", "REPORT",
                "fileName", "malware.exe",
                "contentType", "image/jpeg"
        ))
                .then()
                .statusCode(400)
                .body("isSuccess", equalTo(false))
                .body("code", equalTo("UNSUPPORTED_CONTENT_TYPE_400"));
    }

    @Test
    @DisplayName("파일명이 없으면 400과 바디 검증 에러 코드를 반환한다.")
    void returns400WhenFileNameIsMissing() {
        createPresignedUrl(Map.of(
                "category", "REPORT",
                "contentType", "image/jpeg"
        ))
                .then()
                .statusCode(400)
                .body("isSuccess", equalTo(false))
                .body("code", equalTo("GLOBAL_400_BODY"));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리면 400을 반환한다.")
    void returns400WhenCategoryIsUnknown() {
        createPresignedUrl(Map.of(
                "category", "UNKNOWN_CATEGORY",
                "fileName", "photo.jpg",
                "contentType", "image/jpeg"
        ))
                .then()
                .statusCode(400)
                .body("isSuccess", equalTo(false));
    }

    @Test
    @DisplayName("액세스 토큰 없이 요청하면 401을 반환한다.")
    void returns401WithoutAccessToken() {
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .post("/s3/presigned-url")
                .then()
                .statusCode(401);
    }
}
