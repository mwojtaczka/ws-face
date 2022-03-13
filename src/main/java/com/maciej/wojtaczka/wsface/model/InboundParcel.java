package com.maciej.wojtaczka.wsface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//TODO make it generic
public class InboundParcel {

    UUID receiverId;
    Type type;

    Message messagePayload;
    MessageStatus messageStatus;

    public enum Type {
		PING, MESSAGE, MESSAGE_STATUS
    }

    public enum MessageStatus {
        DELIVERED, READ
    }

}

