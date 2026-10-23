package com.kaoyan408.core.vo;

import lombok.Data;

/** 分科目统计结果。 */
@Data
public class SubjectStats {

    private String subject;

    private Long total;

    private Long done;

    private Long correct;
}