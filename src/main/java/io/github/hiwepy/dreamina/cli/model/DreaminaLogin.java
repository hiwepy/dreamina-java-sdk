package io.github.hiwepy.dreamina.cli.model;

import lombok.Builder;
import lombok.Getter;

/**
 * {@code dreamina login} / {@code login --headless} 解析体。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaLogin {

    private final Boolean oauthSessionReused;
    private final DreaminaLoginAccount account;
    private final DreaminaDeviceLogin device;

    /**
     * @return 是否已复用本地 OAuth 且解析到账户信息
     */
    public boolean hasAccount() {
        return account != null;
    }

    /**
     * @return 是否仅为「复用 OAuth」单行提示（无 JSON、无账户行）
     */
    public boolean isOAuthReuseOnly() {
        return Boolean.TRUE.equals(oauthSessionReused)
            && account == null
            && (device == null || !device.isMaterialPresent());
    }
}
