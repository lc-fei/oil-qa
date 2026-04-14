package org.example.springboot.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data, LocalDateTime.now());
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data, LocalDateTime.now());
    }

    public static <T> Result<T> failure(Integer code, String message) {
        return new Result<>(code, message, null, LocalDateTime.now());
    }
}
