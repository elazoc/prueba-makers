package com.wecaelum.prestemos.infrastructure.config;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Ehcache 3 behind JCache (JSR-107), configured in {@code ehcache.xml}. The {@link CacheManager} is our own bean,
 * so Spring Boot's cache auto-configuration backs off ({@code @ConditionalOnMissingBean}); its cache metrics still
 * bind to every {@code CacheManager} bean. There is no {@code TransactionAwareCacheManagerProxy}: eviction after
 * commit is the job of {@code CacheEvictionListener}, and reads keep storing at once.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    static final String EHCACHE_XML = "classpath:ehcache.xml";

    /**
     * The JCache provider hands back the same manager for the same URI and class loader. Each application
     * context gets its own one (the config URI plus a unique fragment, which loading the file ignores), so it
     * can close it on shutdown without breaking another context, such as a second test context in the same JVM.
     */
    @Bean(destroyMethod = "close")
    public javax.cache.CacheManager jCacheManager(ResourceLoader resourceLoader) throws IOException {
        ClassLoader classLoader = resourceLoader.getClassLoader();
        CachingProvider provider = Caching.getCachingProvider(EhcacheCachingProvider.class.getName(), classLoader);
        URI config = resourceLoader.getResource(EHCACHE_XML).getURI();
        URI perContext = URI.create(config + "#" + UUID.randomUUID());
        return provider.getCacheManager(perContext, classLoader);
    }

    @Bean
    public CacheManager cacheManager(javax.cache.CacheManager jCacheManager) {
        return new JCacheCacheManager(jCacheManager);
    }
}
