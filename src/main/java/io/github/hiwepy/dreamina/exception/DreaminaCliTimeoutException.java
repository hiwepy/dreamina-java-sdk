package io.github.hiwepy.dreamina.exception;

import io.github.hiwepy.dreamina.cli.DreaminaCliResult;
/**
 * ExecuteWatchdog 触发：子进程在配置的超时时间内未结束时抛出。
 *
 * @author wandl
 * @since 1.0.0
 */
public class DreaminaCliTimeoutException extends DreaminaCliException {

    /**
     * @param message        说明性消息
     * @param partialResult  若在此之前已捕获到部分输出则可传入，否则可为 null
     */
    public DreaminaCliTimeoutException(String message, DreaminaCliResult partialResult) {
        super(message, partialResult);
    }
}
