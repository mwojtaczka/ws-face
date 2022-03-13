package com.maciej.wojtaczka.wsface.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;
import redis.embedded.RedisServer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class EmbeddedRedis {

	@Autowired
	RedisProperties redisProperties;

	private static RedisServer redisServer = null;

	@PostConstruct
	public void startRedis() {
		if (redisServer == null || !redisServer.isActive()) {
			redisServer = new RedisServer(redisProperties.getPort());
			redisServer.start();
		}
	}

	@PreDestroy
	public void stopRedis() {
		if (redisServer != null) {
			redisServer.stop();
		}
	}
}
