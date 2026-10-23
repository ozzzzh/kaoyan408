package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户在某知识块上的掌握度（由做题结果聚合而来）。 */
@Data
@TableName("block_progress")
public class BlockProgress {

    private Long userId;

    private String blockId;

    private Integer questionsTotal;

    private Integer questionsDone;

    private Integer questionsCorrect;
}