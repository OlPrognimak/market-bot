package com.prognimak.marketbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AppConfiguration {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(this::configureCodecs)
                        .build());
    }

    private HttpClient httpClient() {
        return HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                .secure(SslProvider.builder()
                        .sslContext(Http11SslContextSpec.forClient())
                        .handshakeTimeout(Duration.ofSeconds(30))
                        .build());
    }

    private void configureCodecs(CodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.configuration")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(30);
        return ds;
    }
}
