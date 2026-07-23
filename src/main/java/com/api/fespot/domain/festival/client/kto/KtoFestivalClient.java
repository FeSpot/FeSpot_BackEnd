package com.api.fespot.domain.festival.client.kto;

import com.api.fespot.domain.festival.client.kto.dto.KtoFestivalResponse;
import com.api.fespot.domain.festival.client.kto.dto.KtoFestivalResponse.Body;
import com.api.fespot.domain.festival.client.kto.dto.KtoFestivalResponse.Item;
import com.api.fespot.domain.festival.client.kto.dto.KtoFestivalResponse.Response;
import com.api.fespot.domain.festival.entity.FestivalReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.global.exception.CustomException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

//OpenAPI를 실제로 호출하는 코드
@Component
public class KtoFestivalClient implements FestivalRepository {

    private static final String SEARCH_FESTIVAL_PATH = "/searchFestival2";
    private static final String BUSAN_LEGAL_REGION_CODE = "26";
    private static final String SUCCESS_RESULT_CODE = "0000";
    private static final String DATA_SOURCE = "한국관광공사 OpenAPI";
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final KtoApiProperties properties;

    public KtoFestivalClient(RestClient.Builder restClientBuilder, KtoApiProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    //FestivalRepository의 구현체
    @Override
    public List<FestivalReadModel> findByYear(int year) {

        //API 키 확인
        if (!StringUtils.hasText(properties.getServiceKey())) {
            throw ktoApiException();
        }

        //연도를 검색 기간으로 변환
        String eventStartDate = Year.of(year).atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String eventEndDate = Year.of(year).atMonth(12).atEndOfMonth()
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        //OpenAPI 호출 메서드 사용 -> 페이징 적용
        Body firstPage = fetchPage(eventStartDate, eventEndDate, 1);
        int totalCount = firstPage.totalCount();
        int pageSize = firstPage.numOfRows() == null || firstPage.numOfRows() <= 0
                ? PAGE_SIZE
                : firstPage.numOfRows();
        List<Item> items = new ArrayList<>(firstPage.itemList());

        for (int page = 2; (long) (page - 1) * pageSize < totalCount; page++) {
            items.addAll(fetchPage(eventStartDate, eventEndDate, page).itemList());
        }

        Instant fetchedAt = Instant.now();
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> toReadModel(item, fetchedAt))
                .toList();
    }

    //OpenAPI 호출
    private Body fetchPage(String eventStartDate, String eventEndDate, int page) {
        KtoFestivalResponse envelope;
        try {
            envelope = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SEARCH_FESTIVAL_PATH)
                            .queryParam("serviceKey", "{serviceKey}")
                            .queryParam("MobileOS", properties.getMobileOs())
                            .queryParam("MobileApp", properties.getMobileApp())
                            .queryParam("_type", "json")
                            .queryParam("eventStartDate", eventStartDate)
                            .queryParam("eventEndDate", eventEndDate)
                            .queryParam("lDongRegnCd", BUSAN_LEGAL_REGION_CODE)
                            .queryParam("numOfRows", PAGE_SIZE)
                            .queryParam("pageNo", page)
                            .queryParam("arrange", "A")
                            .build(properties.getServiceKey()))
                    .retrieve()
                    .body(KtoFestivalResponse.class);
        } catch (RestClientException ignored) {
            throw ktoApiException();
        }

        return validate(envelope);
    }

    private Body validate(KtoFestivalResponse envelope) {
        Response response = envelope == null ? null : envelope.payload();
        if (response == null || response.header() == null) {
            throw ktoApiException();
        }

        if (!SUCCESS_RESULT_CODE.equals(response.header().resultCode())) {
            throw ktoApiException();
        }

        Body body = response.body();
        if (body == null || body.totalCount() == null || body.totalCount() < 0) {
            throw ktoApiException();
        }
        return body;
    }

    private CustomException ktoApiException() {
        return new CustomException(FestivalErrorResponseCode.KTO_API_ERROR_502);
    }

    //OpenAPI의 응답을 우리 서비스의 형식으로 변환
    private FestivalReadModel toReadModel(Item item, Instant fetchedAt) {
        return new FestivalReadModel(
                item.contentid(),
                item.title(),
                joinAddress(item.addr1(), item.addr2()),
                parseDate(item.eventstartdate()),
                parseDate(item.eventenddate()),
                firstText(item.firstimage(), item.firstimage2()),
                item.imageCopyrightType(),
                DATA_SOURCE,
                fetchedAt);
    }

    private String joinAddress(String addr1, String addr2) {
        String address = Stream.of(addr1, addr2)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return StringUtils.hasText(address) ? address : null;
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
