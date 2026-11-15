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

    /** 累计答错次数（答对不清零），用于识别错题与重做效果。 */
    private Integer wrongCount;

    /** 答错之后再次作答且答对的次数。 */
    private Integer retryCorrectCount;

    /** 错题订正成功（移出错题本）的时间；为空表示仍在错题本中。 */
    private LocalDateTime masteredAt;

    /** 首次答错时间，用于错题本排序。 */
    private LocalDateTime firstWrongAt;
}