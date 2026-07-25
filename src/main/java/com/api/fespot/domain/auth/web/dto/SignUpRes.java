package com.api.fespot.domain.auth.web.dto;

import com.api.fespot.domain.user.entity.User;

public record SignUpRes(
        Long userId,
        String email
) {

    public static SignUpRes from(User user) {
        return new SignUpRes(user.getId(), user.getEmail());
    }
}
