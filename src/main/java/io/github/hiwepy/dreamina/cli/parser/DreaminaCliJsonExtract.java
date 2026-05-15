package io.github.hiwepy.dreamina.cli.parser;

/**
 * 从 Dreamina CLI 混杂日志的输出中提取可用的 JSON 片段（对象或数组）。
 * <p>
 * 典型场景：stderr 打印一行初始化告警，stdout 仍为紧凑 JSON；部分命令也可能把 JSON 嵌在多行日志之后。
 * 本工具采用括号配对扫描，尽量避免误吞字符串字面量中的括号。
 * </p>
 *
 * @author wandl
 * @since 1.0.0
 */
final class DreaminaCliJsonExtract {

    private DreaminaCliJsonExtract() {
    }

    /**
     * 在合并文本中查找第一段语法上平衡的 JSON（{@code {...}} 或 {@code [...]}）。
     *
     * @param combined stdout/stderr 合并后的文本；可为 null
     * @return 候选 JSON 子串；无法识别时返回 {@code null}
     */
    static String extractFirstBalancedJson(String combined) {
        if (combined == null || combined.isEmpty()) {
            return null;
        }
        int obj = combined.indexOf('{');
        int arr = combined.indexOf('[');
        int start = -1;
        char open = 0;
        if (obj < 0 && arr < 0) {
            return null;
        }
        if (obj < 0) {
            start = arr;
            open = '[';
        } else if (arr < 0) {
            start = obj;
            open = '{';
        } else {
            start = Math.min(obj, arr);
            open = combined.charAt(start);
        }
        char close = open == '{' ? '}' : ']';
        int end = findClosingIndex(combined, start, open, close);
        if (end <= start) {
            return null;
        }
        return combined.substring(start, end + 1).trim();
    }

    /**
     * 从 {@code start} 处的开括号起扫描到匹配的闭括号 inclusive index。
     *
     * @param text      全文
     * @param start     {@code '{'} 或 {@code '['} 的下标
     * @param openChar  起始括号字符
     * @param closeChar 结束括号字符
     * @return 闭合括号下标；失败返回 {@code -1}
     */
    private static int findClosingIndex(String text, int start, char openChar, char closeChar) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
