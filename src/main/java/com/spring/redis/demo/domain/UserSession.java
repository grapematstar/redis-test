package com.spring.redis.demo.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String deviceId;
    private String userAgent;
    private String token;
    private String sessionId;

}
