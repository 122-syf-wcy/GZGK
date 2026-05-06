package com.gzly.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConfigTest {

    @Test
    void shouldRejectTypesOutsideRedisWhitelist() {
        RedisConfig config = new RedisConfig();
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(config.redisObjectMapper());

        byte[] payload = "{\"@class\":\"java.io.File\",\"path\":\"/tmp/pwn\"}".getBytes();

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class);
    }
}
