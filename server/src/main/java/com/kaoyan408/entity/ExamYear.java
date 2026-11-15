package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("exam_year")
public class ExamYear {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer year;

    private String title;
}