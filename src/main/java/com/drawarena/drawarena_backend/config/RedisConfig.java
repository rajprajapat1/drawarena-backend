package com.drawarena.drawarena_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer
                .builder()
                .enableDefaultTyping(
                        BasicPolymorphicTypeValidator
                                .builder()
                                .allowIfSubType("com.drawarena.drawarena_backend")
                                .allowIfSubType("java.util")
                                .build())
                .build();

        template.setKeySerializer(keySerializer);

        template.setHashKeySerializer(keySerializer);

        template.setValueSerializer(
                valueSerializer);

        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();

        return template;
    }
}