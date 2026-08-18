package com.api.fespot.domain.auth.service.social;

public interface SocialLoginUseCase {

    SocialLoginResult login(SocialLoginCommand command);
}
