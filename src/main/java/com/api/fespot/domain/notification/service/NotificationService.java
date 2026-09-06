package com.api.fespot.domain.notification.service;

import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.exception.PushTokenErrorResponseCode;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserRepository userRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushDeliveryTicketRepository pushDeliveryTicketRepository;


    @Transactional
    public void register(Long userId, String token) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404));

        try{
            PushToken requestedToken = pushTokenRepository.findByToken(token)
                    .orElse(null);

            if(requestedToken != null){
                //같은 사용자의 토큰 재등록
                if(requestedToken.getUser().getId().equals(userId)){
                    return;
                }
                //다른 사용자 소유의 토큰이므로 예외 처리
                throw new CustomException(PushTokenErrorResponseCode.PUSH_TOKEN_OWNED_BY_ANOTHER_USER_409);
            }

            PushToken currentToken = pushTokenRepository.findByUser_Id(userId)
                    .orElse(null);

            //사용자의 현재 토큰을 조회했을 때 현재 토큰이 없다면 요청받은 토큰을 사용자에게 등록
            if(currentToken == null){
                pushTokenRepository.save(PushToken.create(user, token));
            } else{
                //현재 토큰이 있다면 저장된 ticket을 모두 삭제하고 토큰을 재등록
                pushDeliveryTicketRepository.deleteAllByPushTokenId(currentToken.getId());
                currentToken.updateToken(token);
            }

            pushTokenRepository.flush();
        } catch (DataIntegrityViolationException exception){ //무결성 제약이 깨졌을 때 예외처리
            throw new CustomException(PushTokenErrorResponseCode.PUSH_TOKEN_CONCURRENT_REGISTRATION_409);
        }
    }

    @Transactional
    public void unregister(Long userId, String token) {

        //이미 삭제된 경우 DELETE는 멱등하게 종료
        if(userRepository.findByIdForUpdate(userId).isEmpty()){
            return;
        }

        pushTokenRepository.findByToken(token)
                //유저 확인
                .filter(pushToken -> pushToken.getUser().getId().equals(userId))
                .ifPresent(pushToken -> {
                    //자식 ticket 먼저 삭제
                    pushDeliveryTicketRepository.deleteAllByPushTokenId(pushToken.getId());
                    //부모 토큰 삭제
                    pushTokenRepository.delete(pushToken);
                });

    }

    @Transactional(readOnly = true)
    public List<String> findTokens(Long userId) {
        return pushTokenRepository.findTokensByUserId(userId);
    }
}
