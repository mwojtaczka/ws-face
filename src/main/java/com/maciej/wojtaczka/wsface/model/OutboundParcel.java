package com.maciej.wojtaczka.wsface.model;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonRootName("OutboundParcel")
public class OutboundParcel<T> {

    Type type;
	Set<UUID> receiverId;
	T payload;

//    Message messagePayload;
//    MessageStatus messageStatus;

    public enum Type {
        PONG, MESSAGE, MESSAGE_STATUS, NOTIFICATION, TIMELINE_ITEM
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ, FAILED
    }

}

