package io.github.hiwepy.dreamina.cli.opts;

import java.util.List;

/**
 * Dreamina CLI 参数提供者。
 * <p>
 * 用于将强类型请求对象转换为可直接传给执行器的 argv 列表，使执行层保留统一入口，
 * 同时让各类生成命令把参数校验、默认值与文档约束沉淀在自身模型内。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
public interface DreaminaCliArgumentProvider {

    /**
     * 转换为 CLI 参数列表（不含可执行文件名与一级子命令）。
     *
     * @return 已校验的 argv 列表
     */
    List<String> toCliArgs();
}
