package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * {@code dreamina query_result} 请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaQueryResultRequest implements DreaminaCliArgumentProvider {

    private final String submitId;
    private final String downloadDir;

    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(
            args, "--submit_id", DreaminaCliRequestSupport.requireNonBlank(submitId, "submitId"));
        DreaminaCliRequestSupport.addFlag(args, "--download_dir", downloadDir);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
