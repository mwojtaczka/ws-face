package com.maciej.wojtaczka.wsface.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InboundParcel<T> {

    Type type;
	T payload;

    public enum Type {
		PING, MESSAGE, MESSAGE_STATUS
    }

}

