package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 综合题的小问。 */
@Data
@TableName("question_subquestion")
public class QuestionSubQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Integer subNumber;

    private Integer score;

    private String description;
}