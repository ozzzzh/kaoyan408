package com.kaoyan408.core.vo;

import lombok.Data;

/** 分科目统计结果。 */
@Data
public class SubjectStats {

    private String subject;

    private Long total;

    private Long done;

    private Long correct;

    /** 该科目总分值（408 各科分值不均衡，只看题数会失真） */
    private Long scoreTotal;

    /** 已作答的选择题数（正确率的分母；综合题不参与正确率） */
    private Long choiceDone;

    /** 已作答题目覆盖的分值 */
    private Long scoreDone;
}