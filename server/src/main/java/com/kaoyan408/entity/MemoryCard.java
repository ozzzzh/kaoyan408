package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 背诵卡片：用户把某个知识块加入背诵后产生的一条记忆记录。
 * 内容不冗余存文本，仍以 blockId 指向知识块，正文改动可自动同步。
 */
@Data
@TableName("memory_card")
public class MemoryCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String blockId;

    /** 记忆盒序号 0-7，决定下次间隔；越大表示记得越牢。 */
    private Integer box;

    /** 累计复习次数。 */
    private Integer reviewCount;

    /** 复习中自评「记住了」的次数。 */
    private Integer correctCount;

    private LocalDateTime lastReviewedAt;

    /** 下次应复习的日期。 */
    private LocalDate nextReviewDate;

    private LocalDateTime createdAt;

    /** 兼容原表字段：保留但不再作为调度依据。 */
    private Integer difficulty;
}