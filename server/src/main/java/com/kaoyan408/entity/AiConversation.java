package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次 AI 对话（一段会话）。
 *
 * <p>title 单独存一列而不是每次从首条消息现算：会话列表要按更新时间倒序显示几十条，
 * 现算就得为每条会话再查一次消息表，列表页会变成 N+1 查询。
 *
 * <p>游客的对话不入库（与模拟考试一致），所以 userId 允许为空。
 */
@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 由首条提问截取，最长 120 字。 */
    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}