package com.api.fespot.domain.report.scheduler;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.service.ReportEraser;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import com.api.fespot.global.s3.service.S3DeleteService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCleanupScheduler {

    private static final long EXPIRE_MINUTES = 30; //제보 유지 시간
    private static final long CLEANUP_INTERVAL_MS = 60_000; //1분마다 실행

    private final ReportRepository reportRepository;
    private final ReportEraser reportEraser;
    private final S3DeleteService s3DeleteService;

    /*
        1분마다 생성된 지 30분이 지난 제보를 찾아 S3 이미지와 함께 일괄 삭제한다.
        S3 삭제를 트랜잭션 밖에서 먼저 수행하는 이유:
        - DB를 먼저 지우면 S3 삭제 실패 시 이미지 URL을 잃어버려 고아 객체가 남는다.
        - S3는 존재하지 않는 객체 삭제도 성공으로 처리하므로 다음 주기에 재시도해도 안전하다.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    public void deleteExpiredReports() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);

        List<Report> expiredReports = reportRepository.findAllByCreatedAtBefore(threshold);
        if (expiredReports.isEmpty()) {
            return;
        }

        List<Long> deletableIds = new ArrayList<>();
        for (Report report : expiredReports) {
            if (deleteImageIfPresent(report)) {
                deletableIds.add(report.getId());
            }
        }

        reportEraser.deleteReports(deletableIds);

        if (!deletableIds.isEmpty()) {
            log.info("만료 제보 일괄 삭제 완료. expired={}, deleted={}", expiredReports.size(), deletableIds.size());
        }
    }

    //이미지 삭제에 성공해 제보를 삭제해도 되는 경우 true를 반환한다.
    private boolean deleteImageIfPresent(Report report) {
        String imageUrl = report.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }

        try {
            s3DeleteService.deleteByFileUrl(imageUrl);
            return true;
        } catch (CustomException e) {
            //잘못된 URL은 재시도해도 삭제될 수 없으므로 제보만 삭제하고 넘어간다.
            if (e.getBaseResponseCode() == S3ErrorResponseCode.INVALID_FILE_URL) {
                log.warn("만료 제보의 이미지 URL이 유효하지 않아 제보만 삭제. reportId={}, imageUrl={}",
                        report.getId(), imageUrl);
                return true;
            }
            //일시적인 S3 오류는 제보를 남겨두고 다음 주기에 재시도한다.
            log.error("만료 제보의 S3 이미지 삭제 실패. 다음 주기에 재시도. reportId={}", report.getId());
            return false;
        }
    }
}
