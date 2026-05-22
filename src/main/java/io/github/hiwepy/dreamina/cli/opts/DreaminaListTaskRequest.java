package io.github.hiwepy.dreamina.cli.opts;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * {@code dreamina list_task} 请求对象。
 *
 * @author wandl
 * @since 1.0.0
 */
@Getter
@Builder
public class DreaminaListTaskRequest implements DreaminaCliArgumentProvider {

    private final String genStatus;
    private final String genTaskType;
    private final String submitId;
    private final Integer limit;
    private final Integer offset;

    @Singular("additionalArg")
    private final List<String> additionalRawArgs;

    @Override
    public List<String> toCliArgs() {
        List<String> args = new ArrayList<>();
        DreaminaCliRequestSupport.addFlag(args, "--gen_status", genStatus);
        DreaminaCliRequestSupport.addFlag(args, "--gen_task_type", genTaskType);
        DreaminaCliRequestSupport.addFlag(args, "--submit_id", submitId);
        DreaminaCliRequestSupport.requireNonNegative(limit, "limit");
        DreaminaCliRequestSupport.addFlag(args, "--limit", limit);
        DreaminaCliRequestSupport.requireNonNegative(offset, "offset");
        DreaminaCliRequestSupport.addFlag(args, "--offset", offset);
        DreaminaCliRequestSupport.addAdditionalArgs(args, additionalRawArgs);
        return args;
    }
}
