package com.api.fespot.domain.plan;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/api"   // 운영과 동일한 컨텍스트 패스로 검증
)
@Testcontainers(disabledWithoutDocker = true)
public class PlanHttpTest {

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
    @Autowired private FestivalJpaRepository festivalJpaRepository;
    @Autowired private VisitPlanRepository visitPlanRepository;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";

        user = userRepository.save(User.create("plan-http-test@fespot.com", "passwordHash"));
        festivalJpaRepository.save(Festival.create("1", LocalDate.now().plusDays(2).getYear()));
        accessToken = createAccessToken(user);
    }

    // RANDOM_PORT 방식은 서버 스레드에서 트랜잭션이 커밋되므로 테스트마다 직접 정리한다.
    @AfterEach
    void tearDown() {
        visitPlanRepository.deleteAll();
        festivalJpaRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String createAccessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user);
    }

    private Map<String, Object> validCreateBody() {
        return Map.of(
                "contentId", "1",
                "startDate", LocalDate.now().plusDays(2).toString(),
                "targetDate", LocalDate.now().plusDays(1).toString(),
                "plans", List.of(Map.of(
                        "startAt", "10:30:00",
                        "name", "카페 A"
                ))
        );
    }

    private Response createPlan() {
        return given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(validCreateBody())
                .post("/my/plan");
    }

    @Test
    @DisplayName("계획 생성 성공 시 201과 Location 헤더, 생성된 계획을 반환한다.")
    void createPlanReturns201WithLocation() {
        createPlan()
                .then()
                .statusCode(201)
                .header("Location", startsWith("/my/plan/"))
                .body("isSuccess", equalTo(true))
                .body("data.planId", notNullValue())
                .body("data.places", hasSize(1))
                .body("data.places[0].placeName", equalTo("카페 A"))
                // @JsonFormat(locale = ko_KR, pattern = "E MM.dd") 직렬화 검증
                .body("data.targetDate", matchesPattern("^[월화수목금토일] \\d{2}\\.\\d{2}$"));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환한다.")
    void requestWithoutTokenReturns401() {
        given()
                .get("/my/plan")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("변조된 토큰으로 요청하면 401을 반환한다.")
    void requestWithTamperedTokenReturns401() {
        given()
                .header("Authorization", "Bearer " + accessToken + "x")
                .get("/my/plan")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("body 검증 실패 시 400과 fieldErrors 목록을 반환한다.")
    void createPlanWithBlankContentIdReturns400WithFieldErrors() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "contentId", "",
                        "startDate", LocalDate.now().plusDays(2).toString(),
                        "targetDate", LocalDate.now().plusDays(1).toString(),
                        "plans", List.of(Map.of(
                                "startAt", "10:30:00",
                                "name", "카페 A"
                        ))
                ))
                .post("/my/plan")
                .then()
                .statusCode(400)
                .body("isSuccess", equalTo(false))
                .body("code", equalTo("GLOBAL_400_BODY"))
                .body("data.field", hasItem("contentId"));
    }

    @Test
    @DisplayName("깨진 JSON body는 400을 반환한다.")
    void createPlanWithMalformedJsonReturns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body("{ \"contentId\": ")
                .post("/my/plan")
                .then()
                .statusCode(400)
                .body("code", equalTo("GLOBAL_400_BODY"));
    }

    @Test
    @DisplayName("planId 경로 변수에 숫자가 아닌 값을 넣으면 400을 반환한다.")
    void getPlanDetailWithInvalidPathVariableReturns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/my/plan/abc")
                .then()
                .statusCode(400)
                .body("code", equalTo("GLOBAL_400_PARAMETER"));
    }

    @Test
    @DisplayName("size 파라미터가 허용 범위를 벗어나면 400을 반환한다.")
    void getAllPlansWithOutOfRangeSizeReturns400() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/my/plan?size=100")
                .then()
                .statusCode(400)
                .body("code", equalTo("GLOBAL_400_PARAMETER"));
    }

    @Test
    @DisplayName("목록 조회 성공 시 totalCount와 커서 페이지를 반환한다.")
    void getAllPlansReturnsListWithCursorPage() {
        Long planId = createPlan().jsonPath().getLong("data.planId");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/my/plan")
                .then()
                .statusCode(200)
                .body("isSuccess", equalTo(true))
                .body("data.totalCount", equalTo(1))
                .body("data.plans.items", hasSize(1))
                .body("data.plans.items[0].planId", equalTo(planId.intValue()))
                .body("data.plans.pageInfo.hasNext", equalTo(false));
    }

    @Test
    @DisplayName("계획 수정 성공 시 200과 수정된 계획을 반환한다.")
    void updatePlanReturns200WithUpdatedPlan() {
        Response created = createPlan();
        Long planId = created.jsonPath().getLong("data.planId");
        Long placeId = created.jsonPath().getLong("data.places[0].placeId");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "contentId", "1",
                        "startDate", LocalDate.now().plusDays(2).toString(),
                        "targetDate", LocalDate.now().plusDays(1).toString(),
                        "plans", List.of(Map.of(
                                "placeId", placeId,
                                "startAt", "11:30:00",
                                "name", "수정된 카페"
                        ))
                ))
                .patch("/my/plan/" + planId)
                .then()
                .statusCode(200)
                .body("isSuccess", equalTo(true))
                .body("data.places[0].placeName", equalTo("수정된 카페"));
    }

    @Test
    @DisplayName("plans 없이 수정 요청하면 장소 변경 없이 200을 반환한다.")
    void updatePlanWithoutPlansReturns200() {
        Long planId = createPlan().jsonPath().getLong("data.planId");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "contentId", "1",
                        "startDate", LocalDate.now().plusDays(2).toString(),
                        "targetDate", LocalDate.now().plusDays(1).toString()
                ))
                .patch("/my/plan/" + planId)
                .then()
                .statusCode(200)
                .body("isSuccess", equalTo(true))
                .body("data.places", hasSize(1));
    }

    @Test
    @DisplayName("빈 plans 배열로 수정 요청하면 400을 반환한다.")
    void updatePlanWithEmptyPlansReturns400() {
        Long planId = createPlan().jsonPath().getLong("data.planId");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "contentId", "1",
                        "startDate", LocalDate.now().plusDays(2).toString(),
                        "targetDate", LocalDate.now().plusDays(1).toString(),
                        "plans", List.of()
                ))
                .patch("/my/plan/" + planId)
                .then()
                .statusCode(400)
                .body("code", equalTo("GLOBAL_400_BODY"))
                .body("data.field", hasItem("plans"));
    }

    @Test
    @DisplayName("계획 삭제 성공 시 204를 반환한다.")
    void deletePlanReturns204() {
        Long planId = createPlan().jsonPath().getLong("data.planId");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .delete("/my/plan/" + planId)
                .then()
                .statusCode(204);
    }

    @Test
    @DisplayName("존재하지 않는 계획 상세 조회는 404를 반환한다.")
    void getPlanDetailWithNotExistPlanReturns404() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/my/plan/999999")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405를 반환한다.")
    void unsupportedMethodReturns405() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType(ContentType.JSON)
                .body(validCreateBody())
                .put("/my/plan")
                .then()
                .statusCode(405)
                .body("code", equalTo("GLOBAL_405"));
    }
}
