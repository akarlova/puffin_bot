package com.java.boot.puffin_resume_bot.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java.boot.puffin_resume_bot.model.ResumeDraft;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisDraftRepository implements DraftRepository {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public RedisDraftRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(Long uid) {
        return "draft:" + uid;
    }

    @Override
    public Optional<ResumeDraft> get(Long userId) {
        var json = redis.opsForValue().get(key(userId));
        if (json == null || json.isBlank()) return Optional.empty();

        try {
            return Optional.of(mapper.readValue(json, ResumeDraft.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(ResumeDraft draft, long ttlSeconds) {
        try {
            var json = mapper.writeValueAsString(draft);
            redis.opsForValue().set(key(draft.getUserId()), json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save draft to Redis", e);
        }
    }

    @Override
    public void delete(Long userId) {
        redis.delete(key(userId));
    }
}
