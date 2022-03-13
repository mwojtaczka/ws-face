package com.maciej.wojtaczka.wsface.model;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class Message {

    UUID authorId;
    String content;
    UUID conversationId;

}
