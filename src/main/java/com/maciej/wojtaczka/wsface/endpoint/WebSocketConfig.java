package com.maciej.wojtaczka.wsface.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maciej.wojtaczka.wsface.back.InboundDispatcher;
import com.maciej.wojtaczka.wsface.back.ListenersRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {


	@Bean
	HandlerMapping handlerMapping(InboundDispatcher inboundDispatcher,
								  ListenersRegistry listenersRegistry,
								  ObjectMapper objectMapper) {

		WebSocketController webSocketController = new WebSocketController(listenersRegistry, inboundDispatcher, objectMapper);

		Map<String, WebSocketHandler> map = new HashMap<>();

		map.put("/websocket", webSocketController);

		SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
		handlerMapping.setOrder(1);
		handlerMapping.setUrlMap(map);
		return handlerMapping;
	}
}
