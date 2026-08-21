package com.api.fespot.domain.report.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.code.ErrorResponseCode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
            존재하지않으면 -> 요청 좌표로 새로웅ㄴ ReportLocation 생성
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
}
