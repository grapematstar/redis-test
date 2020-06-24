package com.spring.redis.demo.configuration;


import com.spring.redis.demo.domain.UserSession;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DnsResolver;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean(name = "redisConnectionFactory")
    public RedisConnectionFactory redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
                .master("mymaster")
                .sentinel("HOST", 26379);
        sentinelConfig.setPassword(RedisPassword.of("PASSWORD"));
        ClientResources clientResources = ClientResources.builder()
                .socketAddressResolver(resolver)
                .build();
        LettuceClientConfiguration configuration = LettuceClientConfiguration.builder()
                .clientResources(clientResources)
                .build();
        return new LettuceConnectionFactory(sentinelConfig, configuration);
    }

    public MappingSocketAddressResolver resolver =  MappingSocketAddressResolver.create(DnsResolver.unresolved(),
            hostAndPort -> {
                if (hostAndPort.getHostText().startsWith("REDIS_SERVER_HOST")) {
                    return HostAndPort.of("HOST", hostAndPort.getPort());
                }
                return hostAndPort;
            });

    @Bean(name = "redisTemplate")
    public RedisTemplate<String, UserSession> redisTemplate() {
        RedisTemplate<String, UserSession> redisTemplate = new RedisTemplate<>();
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }


}