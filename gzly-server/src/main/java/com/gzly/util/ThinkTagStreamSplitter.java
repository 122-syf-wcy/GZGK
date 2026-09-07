package com.gzly.util;

/**
 * 流式 <think>...</think> 标签拆分器。
 *
 * 部分思维链模型不走 reasoning_content 字段，而是把思考过程用 think 标签
 * 混在 content 增量里输出；SSE 分片可能把标签切成两半（如先收到 "<thi" 再收到 "nk>"），
 * 因此必须带缓冲的状态机解析，尾部疑似半个标签的字符会滞留到下一次 feed。
 */
public class ThinkTagStreamSplitter {

    private static final String OPEN_TAG = "<think>";
    private static final String CLOSE_TAG = "</think>";

    private final StringBuilder buffer = new StringBuilder();
    private boolean inThink = false;

    /** 单次拆分结果：两路都可能为空字符串。 */
    public record Slice(String reasoning, String content) {
        public boolean isEmpty() {
            return reasoning.isEmpty() && content.isEmpty();
        }
    }

    /** 喂入一段增量，返回当前可安全下发的思考/正文两路文本。 */
    public Slice feed(String delta) {
        if (delta != null && !delta.isEmpty()) {
            buffer.append(delta);
        }
        return drain(false);
    }

    /** 流结束时调用：清空缓冲（含尾部滞留的疑似半标签字符）。 */
    public Slice finish() {
        return drain(true);
    }

    private Slice drain(boolean flushAll) {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        while (true) {
            String boundary = inThink ? CLOSE_TAG : OPEN_TAG;
            StringBuilder target = inThink ? reasoning : content;
            int idx = buffer.indexOf(boundary);
            if (idx >= 0) {
                target.append(buffer, 0, idx);
                buffer.delete(0, idx + boundary.length());
                inThink = !inThink;
                continue;
            }
            int safeEnd = flushAll ? buffer.length() : buffer.length() - trailingPrefixLength(boundary);
            if (safeEnd > 0) {
                target.append(buffer, 0, safeEnd);
                buffer.delete(0, safeEnd);
            }
            break;
        }
        return new Slice(reasoning.toString(), content.toString());
    }

    /** buffer 尾部与标签前缀重叠的最大长度（可能是被切断的半个标签）。 */
    private int trailingPrefixLength(String tag) {
        int max = Math.min(tag.length() - 1, buffer.length());
        for (int len = max; len > 0; len--) {
            boolean match = true;
            int offset = buffer.length() - len;
            for (int i = 0; i < len; i++) {
                if (buffer.charAt(offset + i) != tag.charAt(i)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return len;
            }
        }
        return 0;
    }
}
