package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试中的一道作答。
 *
 * <p>考试期间随时保存，交卷前不会丢答案。
 * 选择题的 {@code correct} 在交卷时统一判定；
 * 综合题是主观题，{@code correct} 保持为空，改由 {@code selfScore} 表示自评结果。
 */
@Data
@TableName("mock_answer")
public class MockAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long questionId;

    /** 选择题存选项键（A/B/C/D）；综合题存用户写的要点，可为空。 */
    private String answer;

    /** 选择题交卷时写入 1/0；综合题留空。 */
    private Integer correct;

    /** 综合题自评分（0..该题满分）。 */
    private Integer selfScore;

    private LocalDateTime updatedAt;
}