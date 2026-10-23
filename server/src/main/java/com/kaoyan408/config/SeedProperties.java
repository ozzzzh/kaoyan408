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

    /** 为 true 时即使题库已有数据也重新灌入（用于内容更新后强制刷新）。 */
    private boolean force = false;
}