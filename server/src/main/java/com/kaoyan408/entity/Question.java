package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    /** 源数据题号，例如 exam-2026-1 */
    private String examRef;

    private Integer examNumber;

    /** ds / os / co / cn */
    private String subject;

    private String chapterName;

    /** choice / comprehensive */
    private String questionType;

    private Integer score;

    private String stem;

    /** 选择题标准答案（A/B/C/D）；综合题为空。 */
    private String answer;

    private Integer difficulty;

    private String explanation;

    /** JSON 数组字符串 */
    private String tagsJson;

    /** JSON 数组字符串，元素形如 /exams/2026/images/xxx.webp */
    private String imagePathsJson;

    private LocalDateTime createdAt;
}