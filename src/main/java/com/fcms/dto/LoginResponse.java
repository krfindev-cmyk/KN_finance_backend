package com.fcms.dto;

import com.fcms.model.AppUser;

public class LoginResponse {
    private Long id;
    private String name;
    private String username;
    private String role;
    private String token;

    public LoginResponse() {}

    public LoginResponse(AppUser user, String token) {
        this.id = user.getId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.role = user.getRole().name();
        this.token = token;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
