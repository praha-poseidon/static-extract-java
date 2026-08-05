package org.springframework.web.client;

public class RestTemplate {

    public String getForObject(String url, Class<?> responseType, Object... uriVariables) {
        return url;
    }

    public String postForObject(String url, Object request, Class<?> responseType) {
        return url;
    }
}
