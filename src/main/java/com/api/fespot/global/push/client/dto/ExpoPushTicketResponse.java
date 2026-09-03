package com.api.fespot.global.push.client.dto;

import java.util.List;

public record ExpoPushTicketResponse(
        List<ExpoPushTicket> data
){
}
