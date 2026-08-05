package com.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

public class UserClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${users.base-url:http://fallback.example}")
    private String usersBaseUrl;

    public String getUser(String id) {
        return restTemplate.getForObject(usersBaseUrl + "/api/users/{id}", String.class, id);
    }

    public String createUser() {
        String url = usersBaseUrl + "/api/users";
        return restTemplate.postForObject(url, "body", String.class);
    }
}
