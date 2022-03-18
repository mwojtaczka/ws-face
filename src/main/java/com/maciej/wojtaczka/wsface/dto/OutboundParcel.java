package com.maciej.wojtaczka.wsface.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Set;
import java.util.UUID;

@Value
@Builder
public class OutboundParcel<T> {

    Type type;
	Set<UUID> recipients;
	T payload;

    public enum Type {
        PONG, MESSAGE, MESSAGE_STATUS, NOTIFICATION, TIMELINE_ITEM, FAILURE
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ, FAILED
    }

}

