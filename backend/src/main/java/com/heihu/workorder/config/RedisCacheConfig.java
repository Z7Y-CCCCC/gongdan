package com.heihu.workorder.config;

import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class RedisCacheConfig extends CachingConfigurerSupport {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);
    private static final ObjectMapper TREE_MAPPER = new ObjectMapper();

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.cache.redis.time-to-live:10m}") Duration cacheTtl
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.addMixIn(PageImpl.class, PageImplMixin.class);
        objectMapper.activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                DefaultTyping.EVERYTHING,
                "@class"
        );
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheTtl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .transactionAware()
                .build();
    }

    /**
     * Redis is an acceleration layer. A temporary Redis failure must not turn a
     * normal database request into a failed business request.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache read failed, falling back to source: cache={}, key={}, message={}",
                        cache == null ? "-" : cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis cache write failed, continuing without cache: cache={}, key={}, message={}",
                        cache == null ? "-" : cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache eviction failed, cache will expire naturally: cache={}, key={}, message={}",
                        cache == null ? "-" : cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache clear failed: cache={}, message={}",
                        cache == null ? "-" : cache.getName(), exception.getMessage());
            }
        };
    }

    /** Keeps Spring Data's internal Sort JSON shape out of the cache contract. */
    @JsonDeserialize(using = PageImplDeserializer.class)
    private abstract static class PageImplMixin {
    }

    private static final class PageImplDeserializer extends JsonDeserializer<PageImpl<?>> {
        @Override
        public PageImpl<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode node = TREE_MAPPER.readTree(parser);
            JsonNode contentNode = node.get("content");
            JsonNode itemsNode = contentNode;
            if (contentNode != null && contentNode.isArray() && contentNode.size() == 2
                    && contentNode.get(1).isArray()) {
                itemsNode = contentNode.get(1);
            }
            List<Object> content = new ArrayList<>();
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    content.add(mapper.readValue(itemNode.toString(), Object.class));
                }
            }
            int pageNumber = node.path("number").asInt(0);
            int pageSize = node.path("size").asInt(content.isEmpty() ? 1 : content.size());
            long totalElements = node.path("totalElements").asLong(content.size());
            return new PageImpl<>(content, PageRequest.of(Math.max(pageNumber, 0), Math.max(pageSize, 1)), totalElements);
        }
    }
}
