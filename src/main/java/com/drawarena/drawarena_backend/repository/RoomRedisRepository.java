package com.drawarena.drawarena_backend.repository;

import com.drawarena.drawarena_backend.model.Room;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RoomRedisRepository {

    private static final String ROOM_PREFIX = "drawarena:room:";
    private static final Duration ROOM_TTL = Duration.ofHours(6);
    private final RedisTemplate<String, Object> redisTemplate;

    public RoomRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(String roomId) {
        return ROOM_PREFIX + roomId;
    }

    public void save(Room room) {

        String key = getKey(room.getRoomId());

        redisTemplate.opsForValue()
                .set(key, room, ROOM_TTL);
    }

    public Optional<Room> findById(String roomId) {

        Object value = redisTemplate.opsForValue()
                .get(getKey(roomId));

        if (value instanceof Room room) {
            return Optional.of(room);
        }

        return Optional.empty();
    }

    public boolean exists(String roomId) {

        Boolean exists = redisTemplate.hasKey(getKey(roomId));

        return Boolean.TRUE.equals(exists);
    }

    public void delete(String roomId) {

        redisTemplate.delete(getKey(roomId));
    }

    public void refreshTtl(String roomId) {

        redisTemplate.expire(getKey(roomId), ROOM_TTL);
    }
}