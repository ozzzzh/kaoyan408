package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一张 AI 生成的图解。
 *
 * <p>按问题哈希去重：图解是「内容」而不是「用户的记录」，
 * 两个人问同一个知识点应该共用同一张，不必各生成一遍。
 */
@Data
@TableName("ai_diagram")
public class AiDiagram {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String questionHash;

    private String question;

    /** 已通过结构校验的 DSL，前端直接拿去渲染。 */
    private String dslJson;

    private String model;

    private Integer hitCount;

    private LocalDateTime createdAt;
}