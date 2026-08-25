package com.api.fespot.domain.report.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.report.web.dto.ReportFeedRes;
import com.api.fespot.domain.report.web.dto.ReportSummaryRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.code.ErrorResponseCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final double EARTH_RADIUS_METERS = 6_371_000; //지구의 평균 반지름을 meter로 나타낸 값
    private static final double SAME_LOCATION_RADIUS_METERS = 25; //마커를 같은 위치로 판단하는 거리
    private static final long ACTIVE_REPORT_MINUTES = 30; //대표 마커 유지 시간

    private final UserRepository userRepository;
    private final FestivalJpaRepository festivalJpaRepository;
    private final ReportLocationRepository reportLocationRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public Report createReport(
            Long userId,
            String contentId,
            int year,
            ReportCreateReq request,
            String imageUrl
    ) {
        //토큰에서 얻은 사용자 ID로 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorResponseCode.UNAUTHORIZED));

        //제보 대상 축제 조회
        Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        //새 마커 25m 안에 기존 대표 마커가 있는지 확인한다.
        /*
            존재한다면 -> 기존 ReportLocation 사용
            존재하지않으면 -> 요청 좌표로 새로운 ReportLocation 생성
         */
        ReportLocation reportLocation = findNearestLocation(festival, request)
                //기존 위치가 없을 때만 orElseGet() 내부 코드 실행
                .orElseGet(() -> reportLocationRepository.save(
                        ReportLocation.create(
                                festival,
                                request.latitude(),
                                request.longitude())));

        Report report = Report.create(
                user,
                reportLocation,
                request.content(),
                request.congestionLevel(),
                imageUrl);

        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public CursorPage<ReportFeedRes> getReportFeedList(
            String contentId,
            int year,
            Long cursor,
            int size
    ) {
        //제보 대상 축제 조회
        Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        //다음 페이지 존재 여부를 확인하기 위해 요청 개수보다 1개 더 조회
        List<Report> fetchReports = reportRepository.findFeedByFestival(festival, cursor, Limit.of(size + 1));

        boolean hasNext = fetchReports.size() > size;

        // 추가로 조회한 1개는 실제 응답에서 제외
        List<Report> pageReports = hasNext
                ? fetchReports.subList(0, size)
                : fetchReports;

        LocalDateTime activeSince = LocalDateTime.now()
                .minusMinutes(ACTIVE_REPORT_MINUTES);

        List<ReportFeedRes> items = pageReports.stream()
                .map(report -> ReportFeedRes.from(report, activeSince))
                .toList();

        // 다음 요청에서는 이번에 실제로 반환한 마지막 제보 ID를 전달
        Long nextCursor = hasNext
                ? pageReports.get(pageReports.size() - 1).getId()
                : null;

        return CursorPage.of(items, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryRes> getReportSummaryList(
            String contentId,
            int year
    ) {
        //제보 요약을 볼 대상 축제 조회
        Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        //현재시간으로부터 30분이 지나지 않은 제보만 가져와야하므로 시간을 계산 ex) 15:00 - 30분 = 14:30
        LocalDateTime activeSince = LocalDateTime.now().minusMinutes(ACTIVE_REPORT_MINUTES);

        //축제와, activeSince을 넣어 30분이 지나지 않은 제보만 가져온다.
        List<Report> reportList = reportRepository.findActiveByFestival(festival, activeSince);

        Map<Long, List<Report>> reportsByLocation = reportList.stream()
                .collect(Collectors.groupingBy(
                        report -> report.getReportLocation().getId()
                ));

        return reportsByLocation.entrySet().stream()
                .map(entry -> {

                    Long reportLocationId = entry.getKey();
                    List<Report> reports = entry.getValue();

                    int reportCount = reports.size();
                    CongestionLevel representativeLevel = null;

                    if (reportCount >= 3) {
                        representativeLevel = calculateRepresentativeLevel(reports);
                    }

                    // 제보가 3미만인 상황에 대해서는 생각을 해보고 있는 중

                    //가장 최신의 제보 시간을 계산
                    LocalDateTime latestReportedAt = reports.stream()
                            .map(Report::getCreatedAt)
                            .max(LocalDateTime::compareTo)
                            .orElseThrow();


                    return ReportSummaryRes.of(reportLocationId, reportCount, representativeLevel, latestReportedAt);
                })

                //groupingBy로 묶으면 기본적으로 hashMap이 만들어지는데 이는 순서를 보장해주지 않는.
                //그래서 DTO를 만들고 정렬을 통해 순서를 보장해준다.
                .sorted(Comparator
                        .comparing(ReportSummaryRes::latestReportedAt)
                        .reversed()
                ).toList();
    }

    //가장 가까운 대표 위치 선택
    private Optional<ReportLocation> findNearestLocation(
            Festival festival,
            ReportCreateReq request
    ) {
        //ex) 제보 시각이 15시라 activeSince는 14시30분
        LocalDateTime activeSince = LocalDateTime.now()
                .minusMinutes(ACTIVE_REPORT_MINUTES);

        //같은 축제에서 14시 30분 이후에 존재하는 대표 위치만 반환
        return reportLocationRepository.findActiveByFestival(
                        festival,
                        activeSince)
                .stream()
                //반환된 대표 위치와 현재 위치 계산
                .filter(location -> distanceFrom(location, request)
                        <= SAME_LOCATION_RADIUS_METERS)
                //25m 초과하는 대표위치는 제거하고 가장 가까운 대표위치 선정
                .min(Comparator.comparingDouble(
                        location -> distanceFrom(location, request)));
    }

    //Haversine 공식을 구현한 메서드
    private double distanceFrom(
            ReportLocation location,
            ReportCreateReq request
    ) {
        double latitude1 = Math.toRadians(location.getAnchorLatitude());
        double latitude2 = Math.toRadians(request.latitude());
        double latitudeDifference = Math.toRadians(
                request.latitude() - location.getAnchorLatitude());
        double longitudeDifference = Math.toRadians(
                request.longitude() - location.getAnchorLongitude());

        double haversine = Math.pow(Math.sin(latitudeDifference / 2), 2)
                + Math.cos(latitude1)
                * Math.cos(latitude2)
                * Math.pow(Math.sin(longitudeDifference / 2), 2);

        double centralAngle = 2 * Math.atan2(
                Math.sqrt(haversine),
                Math.sqrt(1 - haversine));

        return EARTH_RADIUS_METERS * centralAngle;
    }

    //대표 혼잡도를 계산하는 메서드 (최빈값을 구하는 로직)
    private CongestionLevel calculateRepresentativeLevel(List<Report> reports) {

        //Enum을 키로 가지는 Map임을 명시
        Map<CongestionLevel, Integer> levelCount = new EnumMap<>(CongestionLevel.class);

        for (Report report : reports) {
            CongestionLevel level = report.getCongestionLevel();

            int currentCount = levelCount.getOrDefault(level, 0);
            levelCount.put(level, currentCount + 1);
        }

        //같은 count일때 어떤 혼잡도를 대표 혼잡도로 명시할지 결정하는 우선순위
        List<CongestionLevel> priority = List.of(
                CongestionLevel.DANGER,
                CongestionLevel.CROWDED,
                CongestionLevel.NORMAL,
                CongestionLevel.SAFE
        );

        CongestionLevel representativeLevel = null; //대표 혼잡도
        int maxCount = 0; //대표 혼잡도가 카운트된 수

        for (CongestionLevel level : priority) {
            //level에 맞는 카운트를 가져와서 계산한다
            int count = levelCount.getOrDefault(level, 0);
            if (count > maxCount) {
                representativeLevel = level;
                maxCount = count;
            }
        }
        return representativeLevel;
    }
}
