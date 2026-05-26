package io.github.hiwepy.dreamina;

import lombok.Data;

/**
 * Dreamina 命令行客户端（CLI）运行时配置（纯 POJO，无 Spring 耦合）。
 * <p>
 * 描述可执行文件路径、工作目录、单次命令超时以及编排侧可选的默认轮询间隔；
 * 仅服务于「如何启动子进程」，不承载业务编排。Spring Boot 应用中可由上层使用
 * {@code @ConfigurationProperties(prefix = "dreamina.cli")} 继承或委托绑定同一字段。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
@Data
public class DreaminaCliProperties {

    /**
     * Dreamina CLI 可执行文件名或绝对路径。
     * <p>默认假定已在 {@code PATH} 中可直接调用 {@code dreamina}。</p>
     */
    private String executable = "dreamina";

    /**
     * 子进程工作目录；为空时使用当前 JVM 工作目录。
     */
    private String workingDirectory;

    /**
     * 单次 CLI 调用超时（毫秒）。
     * <p>
     * 用于 {@link org.apache.commons.exec.ExecuteWatchdog}；超时后将终止子进程并映射为执行层超时异常。
     * </p>
     */
    private long commandTimeoutMillis = 120_000L;

    /**
     * 本机 CLI 子进程最大并发数；小于等于 0 时使用 CPU 核心数与 2 的较大值。
     */
    private int maxConcurrentExecutions = 0;

    /**
     * 编排层默认轮询间隔（秒）。
     * <p>
     * 例如在 {@code query_result} 与 {@code text2image} 异步链路之间休眠间隔的可复用默认值；
     * 本执行器仅暴露配置，不负责具体轮询实现。
     * </p>
     */
    private int defaultPollIntervalSeconds = 5;
}
