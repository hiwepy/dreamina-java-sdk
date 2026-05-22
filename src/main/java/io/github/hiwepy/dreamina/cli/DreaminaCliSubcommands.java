package io.github.hiwepy.dreamina.cli;

/**
 * Dreamina（即梦）CLI 子命令字面量常量：一级命令按能力域分组；{@link LoginSub}、{@link SessionSub} 为
 * {@code login} / {@code session} 后的二级 token，供执行器拼装 {@code dreamina login checklogin}、
 * {@code dreamina session create} 等链路。
 * <p>
 * 与 OpenClaw / Jimeng 技能文档中的命令划分对齐；具体 flag 仍由调用方通过便捷方法或
 * {@link DreaminaCliExecutor#invoke(String, java.util.List)} 追加。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
public final class DreaminaCliSubcommands {

    private DreaminaCliSubcommands() {}

    /**
     * 一级内置命令（帮助、任务列表等）。
     */
    public static final class Builtin {

        private Builtin() {}

        /** {@code dreamina help}（可再接子命令名，如 {@code dreamina help login}） */
        public static final String HELP = "help";
    }

    /**
     * 账号与会话：版本自检、额度、登录态、会话上下文等。
     */
    public static final class Account {

        private Account() {}

        /** {@code dreamina version} */
        public static final String VERSION = "version";

        /** {@code dreamina user_credit} */
        public static final String USER_CREDIT = "user_credit";

        /** {@code dreamina login}（可加 {@code --debug} / {@code --headless} 等后缀参数） */
        public static final String LOGIN = "login";

        /** {@code dreamina logout} */
        public static final String LOGOUT = "logout";

        /** {@code dreamina relogin} */
        public static final String RELOGIN = "relogin";

        /** {@code dreamina session} */
        public static final String SESSION = "session";
    }

    /**
     * {@code dreamina login} 下的子动作（二级 token，位于 {@code login} 之后）。
     */
    public static final class LoginSub {

        private LoginSub() {}

        /**
         * {@code dreamina login checklogin ...}：无头 / 设备码流程下轮询完成授权。
         */
        public static final String CHECKLOGIN = "checklogin";
    }

    /**
     * {@code dreamina session} 下的子动作（二级 token，位于 {@code session} 之后）。
     */
    public static final class SessionSub {

        private SessionSub() {}

        /** {@code dreamina session create} */
        public static final String CREATE = "create";

        /** {@code dreamina session list} */
        public static final String LIST = "list";

        /** {@code dreamina session ls}：{@link #LIST} 的官方别名。 */
        public static final String LS = "ls";

        /** {@code dreamina session search} */
        public static final String SEARCH = "search";

        /** {@code dreamina session find}：{@link #SEARCH} 的官方别名。 */
        public static final String FIND = "find";

        /** {@code dreamina session rename} */
        public static final String RENAME = "rename";

        /** {@code dreamina session update}：{@link #RENAME} 的官方别名。 */
        public static final String UPDATE = "update";

        /** {@code dreamina session delete} */
        public static final String DELETE = "delete";

        /** {@code dreamina session rm}：{@link #DELETE} 的官方别名。 */
        public static final String RM = "rm";
    }

    /**
     * 图片生成与编辑相关子命令。
     */
    public static final class Image {

        private Image() {}

        /** {@code dreamina text2image} */
        public static final String TEXT2IMAGE = "text2image";

        /** {@code dreamina image2image}（图生图） */
        public static final String IMAGE2IMAGE = "image2image";

        /** {@code dreamina image_upscale} */
        public static final String IMAGE_UPSCALE = "image_upscale";
    }

    /**
     * 视频生成相关子命令（含图生视频的多种输入形态）。
     */
    public static final class Video {

        private Video() {}

        /** {@code dreamina text2video} */
        public static final String TEXT2VIDEO = "text2video";

        /** {@code dreamina image2video}（单图驱动） */
        public static final String IMAGE2VIDEO = "image2video";

        /** {@code dreamina frames2video}（首尾帧） */
        public static final String FRAMES2VIDEO = "frames2video";

        /** {@code dreamina multiframe2video}（多图分镜） */
        public static final String MULTIFRAME2VIDEO = "multiframe2video";

        /** {@code dreamina multimodal2video}（多模态合成） */
        public static final String MULTIMODAL2VIDEO = "multimodal2video";
    }

    /**
     * 任务列表与结果查询。
     */
    public static final class Task {

        private Task() {}

        /** {@code dreamina query_result} */
        public static final String QUERY_RESULT = "query_result";

        /** {@code dreamina list_task} */
        public static final String LIST_TASK = "list_task";
    }
}
