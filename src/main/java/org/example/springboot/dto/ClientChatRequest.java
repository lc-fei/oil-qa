package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户端发送问题请求对象。
 */
@Data
public class ClientChatRequest {

    private Long sessionId;

    @NotBlank(message = "question不能为空")
    private String question;

    private String contextMode = "ON";
    private String answerMode = "GRAPH_ENHANCED";
}
