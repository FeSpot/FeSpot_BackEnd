package com.api.fespot.domain.report.service;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportLikeService {
    private final ReportLikeRepository reportLikeRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;

    public void updateReportLike(Long userId, Long reportId, boolean liked) {
        User user = userRepository.findById(userId)
                .orElseThrow(()-> new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404));

        Report report = reportRepository.findById(reportId)
                .orElseThrow(()-> new CustomException(ReportErrorResponseCode.REPORT_NOT_FOUND_404));

        Optional<ReportLike> existingLike = reportLikeRepository.findByUserAndReport(user, report);

        if(liked) {
            if(existingLike.isEmpty()){
                reportLikeRepository.save(ReportLike.create(user, report));
            }
        } else {
            existingLike.ifPresent(reportLikeRepository::delete);
        }
    }
}
