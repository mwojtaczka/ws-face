package com.maciej.wojtaczka.wsface.back;

import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
public class ListenersRegistry {

	private final String appInstanceName;
	private final ConcurrentMap<UUID, PersonalListener<OutboundParcel<?>>> inMemoryRegistry = new ConcurrentHashMap<>();
	private final ReactiveStringRedisTemplate redisTemplate;

	public ListenersRegistry(String appInstanceName, ReactiveStringRedisTemplate redisTemplate) {
		this.appInstanceName = appInstanceName;
		this.redisTemplate = redisTemplate;
	}

	public Mono<Void> register(PersonalListener<OutboundParcel<?>> listener) {
		return redisTemplate.opsForValue().set(listener.getOwnerID().toString(), appInstanceName)
							.doOnNext(b -> {
								if (b) {
									inMemoryRegistry.put(listener.getOwnerID(), listener);
									log.info("Listener for user: {} registered in app: {}", listener.getOwnerID(), appInstanceName);
								} else {
									log.warn("Listener for user: {} *NOT* registered in app: {}", listener.getOwnerID(), appInstanceName);
								}
							})
							.flatMap(b -> b? Mono.empty() : Mono.error(() -> new RuntimeException("Could NOT registered")));
	}

	public Mono<Void> unregister(UUID ownerId) {
		inMemoryRegistry.remove(ownerId);

		return redisTemplate.opsForValue().delete(ownerId.toString())
				.doOnNext(b -> {
					if (b) {
						log.info("Listener for user: {} unregistered in app: {}", ownerId, appInstanceName);
					} else {
						log.error("Listener for user: {} *COULD NOT BE* unregistered in app: {}", ownerId, appInstanceName);
					}
				})
							.flatMap(b -> Mono.empty());
	}

	Set<PersonalListener<OutboundParcel<?>>> get(Collection<UUID> owners) {
		HashSet<PersonalListener<OutboundParcel<?>>> temp = new HashSet<>();
		owners.forEach(owner -> temp.add(inMemoryRegistry.get(owner)));
		return temp.stream()
				   .filter(Objects::nonNull)
				   .collect(Collectors.toSet());

	}

}
