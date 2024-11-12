package no.bankaxept.home.assignment.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.bankaxept.home.assignment.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ServiceConfiguration {

    @Value("${rest.template.connectTimeout}")
    private int connectTimeout;

    @Value("${rest.template.readTimeout}")
    private int readTimeout;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout); // 10 seconds
        factory.setReadTimeout(readTimeout);    // 10 seconds
        return new RestTemplate(factory);
    }

    @Bean
    public PaymentService paymentService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate, ObjectMapper objectMapper) {
        return new PaymentService(jdbcTemplate, restTemplate, objectMapper);
    }

}
