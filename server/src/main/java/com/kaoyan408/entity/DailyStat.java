package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("daily_stat")
public class DailyStat {

    private Long userId;

    private LocalDate statDate;

    private Integer questions;

    private Integer correct;

    private Integer minutes;
}