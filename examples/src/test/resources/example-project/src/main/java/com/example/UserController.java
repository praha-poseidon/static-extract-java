package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api")
public class UserController {

    @GetMapping("/users/{id}")
    public String getUser(@PathVariable String id) {
        return id;
    }

    @PostMapping(path = "/users")
    public String createUser() {
        return "created";
    }
}
