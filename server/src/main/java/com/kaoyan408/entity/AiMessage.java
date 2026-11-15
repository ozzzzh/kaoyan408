package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条 AI 会话消息。
 *
 * <p>只存正文（content），不存模型的推理内容（reasoning）。
 * 推理过程对用户没有使用价值，留着既占空间又可能泄露提示词结构。
 *
 * <p>token 用量只在 assistant 消息上记，用于估算成本；不记录原始请求体。
 */
@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private LocalDateTime createdAt;
}