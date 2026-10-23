package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_attempt")
public class QuestionAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionId;

    /** 1 = 正确，0 = 错误 */
    private Integer result;

    private Integer hintsUsed;

    private Integer durationMs;

    private String answer;

    private LocalDateTime answeredAt;
}