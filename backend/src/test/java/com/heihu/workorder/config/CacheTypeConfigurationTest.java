package com.heihu.workorder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CacheTypeConfigurationTest {

    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withUserConfiguration(CachingConfiguration.class, RedisCacheConfig.class)
            .withBean(RedisConnectionFactory.class, () -> connectionFactory);

    @Test
    void noneUsesNoOpCacheAndDoesNotConnectToRedis() {
        runner.withPropertyValues("spring.cache.type=none").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(NoOpCacheManager.class);
            context.getBean(CacheManager.class).getCache("test").put("key", "value");
            verifyNoInteractions(connectionFactory);
        });
    }

    @Test
    void simpleUsesLocalCacheAndDoesNotConnectToRedis() {
        runner.withPropertyValues("spring.cache.type=simple").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CacheManager.class)).isInstanceOf(ConcurrentMapCacheManager.class);
            context.getBean(CacheManager.class).getCache("test").put("key", "value");
            verifyNoInteractions(connectionFactory);
        });
    }

    @Test
    void explicitRedisAndDefaultKeepRedisCacheManager() {
        runner.run(context -> assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class));
        runner.withPropertyValues("spring.cache.type=redis")
                .run(context -> assertThat(context.getBean(CacheManager.class)).isInstanceOf(RedisCacheManager.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingConfiguration { }
}
