package com.xduo.springbootinit.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

/**
 * ES 8 客户端配置（适配 Spring Boot 3）
 */
@Configuration
public class Es8Config {

    @Value("${spring.elasticsearch.username}")
    private String username;

    @Value("${spring.elasticsearch.password}")
    private String password;

    @Value("${app.elasticsearch.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${app.elasticsearch.socket-timeout-ms:3000}")
    private int socketTimeoutMs;

    @Value("${app.elasticsearch.connection-request-timeout-ms:1000}")
    private int connectionRequestTimeoutMs;

    /**
     * 自定义 RestClientBuilder，实现免 SSL 证书校验和基础认证
     * Spring Boot 3 会自动使用此 Customizer 构造 RestClient、ElasticsearchClient 和 ElasticsearchTemplate
     */
    @Bean
    public RestClientBuilderCustomizer restClientBuilderCustomizer() {
        return builder -> {
            try {
                // 1. 信任所有证书 (处理自签名证书)
                SSLContext sslContext = SSLContextBuilder.create()
                        .loadTrustMaterial(new TrustAllStrategy())
                        .build();

                // 2. 构造身份认证信息
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

                // 3. 应用配置
                // 注意：在 SB3 中，如果手动 setHttpClientConfigCallback，会覆盖自动配置的逻辑
                // 因此我们必须在这里同时包含 SSL 和身份认证
                builder.setRequestConfigCallback(requestConfigBuilder -> RequestConfig.copy(requestConfigBuilder.build())
                        .setConnectTimeout(connectTimeoutMs)
                        .setSocketTimeout(socketTimeoutMs)
                        .setConnectionRequestTimeout(connectionRequestTimeoutMs));
                builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .setDefaultCredentialsProvider(credentialsProvider));
                
            } catch (Exception e) {
                throw new RuntimeException("ES 客户端配置失败", e);
            }
        };
    }
}
