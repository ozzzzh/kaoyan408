package com.kaoyan408.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 内容种子加载开关。 */
@Data
@Component
@ConfigurationProperties(prefix = "kaoyan.seed")
public class SeedProperties {

    /** 是否在启动时检查并加载内容种子。 */
    private boolean enabled = true;

    /**
     * 为 true 时即使题库已有数据也重新灌入（用于内容更新后强制刷新）。
     *
     * <p>注意：重灌会 DELETE 题库并重新插入，question.id 随之改变，
     * 已有的作答记录会变成孤儿。因此当检测到用户作答数据时，本开关会被忽略，
     * 需要同时设置 {@link #forceWithUserData} 才真正执行。
     */
    private boolean force = false;

    /**
     * 明确确认「连用户作答数据一起丢弃」。
     *
     * <p>单独一个开关是为了让「重灌内容」和「清空用户历史」成为两个不同的决定：
     * 前者是运维常规操作，后者不可逆。
     */
    private boolean forceWithUserData = false;
}