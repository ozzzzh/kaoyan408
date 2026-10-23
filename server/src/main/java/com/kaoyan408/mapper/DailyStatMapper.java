package com.kaoyan408.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaoyan408.entity.DailyStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DailyStatMapper extends BaseMapper<DailyStat> {

    @Select("SELECT stat_date, questions, correct, minutes FROM daily_stat "
            + "WHERE user_id = #{userId} ORDER BY stat_date DESC LIMIT #{limit}")
    List<DailyStat> recent(@Param("userId") Long userId, @Param("limit") int limit);
}