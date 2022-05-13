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
        PONG,
		STATUS,
		MESSAGE,
		MESSAGE_STATUS,
		CONNECTION_REQUESTED,
		CONNECTION_CREATED,
		GENERIC,
		ANNOUNCEMENT
    }

    public enum Status {
        SENT, FAILED
    }

}

