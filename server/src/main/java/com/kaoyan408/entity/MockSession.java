package com.kaoyan408.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一场模拟考试。
 *
 * <p>剩余时间由 {@code startedAt + durationMinutes} 计算，不存倒计时数值——
 * 刷新页面、换设备、甚至服务重启后都能回到正确的剩余时间。
 *
 * <p>分数拆成两个字段：{@code autoScore} 是选择题自动判分（满分 80），
 * {@code selfScore} 是综合题用户自评（满分 70）。408 的综合题是主观题，
 * 无法自动判分，硬要自动判只会给出错误成绩。
 */
@Data
@TableName("mock_session")
public class MockSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer examYear;

    private LocalDateTime startedAt;

    /** 限时（分钟）。0 表示不限时。 */
    private Integer durationMinutes;

    /** ongoing 进行中 / submitted 已交卷 */
    private String status;

    private LocalDateTime submittedAt;

    /** 选择题自动得分（满分 80）。 */
    private Integer autoScore;

    /** 综合题自评得分（满分 70）。 */
    private Integer selfScore;

    /** 交卷时是否因超时被强制交卷。 */
    private Integer timedOut;
}