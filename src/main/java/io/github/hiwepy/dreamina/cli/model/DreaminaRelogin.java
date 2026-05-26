package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina relogin} 解析体。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaRelogin {

    private final Boolean requiresBrowserOAuth;
    private final DreaminaDeviceLogin device;

    /**
     * @return 是否应继续 {@code checklogin}
     */
    public boolean needsCheckLogin() {
        return device != null && device.isMaterialPresent();
    }
}
