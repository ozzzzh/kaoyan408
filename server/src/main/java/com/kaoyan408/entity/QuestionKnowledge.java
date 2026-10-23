package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("question_knowledge")
public class QuestionKnowledge {

    private Long questionId;

    private String blockId;
}