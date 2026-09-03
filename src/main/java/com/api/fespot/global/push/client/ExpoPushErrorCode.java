package com.api.fespot.global.push.client;

public enum ExpoPushErrorCode {
    DEVICE_NOT_REGISTERED("DeviceNotRegistered"),
    UNKNOWN("");

    private final String value;

    ExpoPushErrorCode(String value) {
        this.value = value;
    }

    public static ExpoPushErrorCode from(String rawValue) {
        if (rawValue == null) {
            return UNKNOWN;
        }

        for (ExpoPushErrorCode code : values()) {
            if (code.value.equals(rawValue)) {
                return code;
            }
        }

        return UNKNOWN;
    }
}
