package io.github.hiwepy.dreamina.cli.availability;

/**
 * Dreamina CLI 可用性探测结论分类。
 *
 * @author wandl
 * @since 1.0.0
 */
public enum DreaminaCliAvailabilityStatus {

    /** {@code dreamina version} 探测成功，CLI 可用。 */
    AVAILABLE,

    /** 未配置可执行文件路径或命令名。 */
    EXECUTABLE_NOT_CONFIGURED,

    /** 配置的路径在文件系统中不存在。 */
    EXECUTABLE_NOT_FOUND,

    /** 路径存在但不可执行。 */
    EXECUTABLE_NOT_EXECUTABLE,

    /** 进程无法启动（PATH 中找不到、权限不足等）。 */
    SPAWN_FAILED,

    /** 进程非零退出。 */
    NON_ZERO_EXIT,

    /** 探测超时。 */
    TIMEOUT,

    /** 其它执行层失败。 */
    FAILED
}
