package com.api.fespot.domain.festival.client.kto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.fespot.domain.festival.entity.FestivalReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KtoFestivalClientTest {

    private MockRestServiceServer server;
    private KtoApiProperties properties;
    private KtoFestivalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        properties = new KtoApiProperties();
        properties.setBaseUrl("https://example.test/KorService2");
        properties.setServiceKey("test+service/key=");
        client = new KtoFestivalClient(restClientBuilder, properties);
    }

    @Test
    void requestsBusanFestivalsAndMapsWrappedArrayResponse() {
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("serviceKey=test%2Bservice%2Fkey%3D"))
                .andExpect(queryParam("MobileOS", "ETC"))
                .andExpect(queryParam("MobileApp", "FeSpot"))
                .andExpect(queryParam("_type", "json"))
                .andExpect(queryParam("eventStartDate", "20260101"))
                .andExpect(queryParam("eventEndDate", "20261231"))
                .andExpect(queryParam("lDongRegnCd", "26"))
                .andExpect(queryParam("numOfRows", "100"))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("arrange", "A"))
                .andRespond(withSuccess(successResponse(2, 100, 1, """
                        [
                          {
                            "contentid": "100",
                            "title": "부산불꽃축제",
                            "addr1": "부산광역시 수영구",
                            "addr2": "광안해변로 219",
                            "eventstartdate": "20261107",
                            "eventenddate": "20261107",
                            "firstimage": "https://example.com/fireworks.jpg",
                            "firstimage2": "https://example.com/fireworks-small.jpg",
                            "cpyrhtDivCd": "Type1",
                            "unknownField": "ignored"
                          },
                          {
                            "contentid": "300",
                            "title": "부산국제록페스티벌",
                            "eventstartdate": "20261002",
                            "eventenddate": "20261004",
                            "firstimage2": "https://example.com/rock-small.jpg"
                          }
                        ]
                        """), MediaType.APPLICATION_JSON));

        List<FestivalReadModel> result = client.findByYear(2026);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).contentId()).isEqualTo("100");
        assertThat(result.get(0).address()).isEqualTo("부산광역시 수영구 광안해변로 219");
        assertThat(result.get(0).startDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(result.get(0).imageUrl()).isEqualTo("https://example.com/fireworks.jpg");
        assertThat(result.get(0).imageCopyrightType()).isEqualTo("Type1");
        assertThat(result.get(0).fetchedAt()).isNotNull();
        assertThat(result.get(1).imageUrl()).isEqualTo("https://example.com/rock-small.jpg");
        server.verify();
    }

    @Test
    void acceptsSingleItemAndReadsEveryPage() {
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(successResponse(2, 1, 1, """
                        {
                          "contentid": "100",
                          "title": "부산불꽃축제",
                          "eventstartdate": "20261107",
                          "eventenddate": "20261107"
                        }
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andExpect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(successResponse(2, 1, 2, """
                        {
                          "contentid": "300",
                          "title": "부산국제록페스티벌",
                          "eventstartdate": "20261002",
                          "eventenddate": "20261004"
                        }
                        """), MediaType.APPLICATION_JSON));

        List<FestivalReadModel> result = client.findByYear(2026);

        assertThat(result)
                .extracting(FestivalReadModel::contentId)
                .containsExactly("100", "300");
        server.verify();
    }

    @Test
    void mapsSwaggerDirectResponseWithEmptyItemsObjectToEmptyList() {
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "header": {"resultCode": "0000", "resultMsg": "OK"},
                          "body": {
                            "totalCount": 0,
                            "items": {},
                            "numOfRows": 100,
                            "pageNo": 1
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FestivalReadModel> result = client.findByYear(2026);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    void rejectsFailedKtoResultCodeWithoutExposingResultMessage() {
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andRespond(withSuccess("""
                        {
                          "response": {
                            "header": {"resultCode": "22", "resultMsg": "LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR"}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findByYear(2026))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.KTO_API_ERROR_502));
        server.verify();
    }

    @Test
    void rejectsRequestBeforeCallingKtoWhenServiceKeyIsMissing() {
        properties.setServiceKey(" ");

        assertThatThrownBy(() -> client.findByYear(2026))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.KTO_API_ERROR_502));
        server.verify();
    }

    @Test
    void convertsHttpFailureToCustomException() {
        server.expect(requestTo(startsWith("https://example.test/KorService2/searchFestival2")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.findByYear(2026))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.KTO_API_ERROR_502));
        server.verify();
    }

    private String successResponse(int totalCount, int numOfRows, int pageNo, String itemJson) {
        return """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "totalCount": %d,
                      "items": {"item": %s},
                      "numOfRows": %d,
                      "pageNo": %d
                    }
                  }
                }
                """.formatted(totalCount, itemJson, numOfRows, pageNo);
    }
}
