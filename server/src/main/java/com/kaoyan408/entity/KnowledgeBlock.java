package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 知识块：知识正文的最小单元，题目通过 block_id 关联到它。 */
@Data
@TableName("knowledge_block")
public class KnowledgeBlock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pointId;

    private String blockId;

    private String subpointId;

    private String subpointTitle;

    private Integer blockOrder;

    /** paragraph / html / formula / callout / image / animation */
    private String type;

    /** 块内容的 JSON 字符串 */
    private String contentJson;
}