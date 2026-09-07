package com.gzly.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式 think 标签拆分器测试：重点覆盖标签被 SSE 分片切断的场景。
 */
class ThinkTagStreamSplitterTest {

    @Test
    void feed_splitsReasoningAndContent() {
        ThinkTagStreamSplitter splitter = new ThinkTagStreamSplitter();
        ThinkTagStreamSplitter.Slice slice = splitter.feed("<think>先分析位次</think>结论是稳妥");
        assertThat(slice.reasoning()).isEqualTo("先分析位次");
        assertThat(slice.content()).isEqualTo("结论是稳妥");
    }

    @Test
    void feed_handlesTagSplitAcrossChunks() {
        ThinkTagStreamSplitter splitter = new ThinkTagStreamSplitter();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();

        for (String chunk : new String[]{"前言<thi", "nk>思考", "中</thi", "nk>正文", "继续"}) {
            ThinkTagStreamSplitter.Slice slice = splitter.feed(chunk);
            reasoning.append(slice.reasoning());
            content.append(slice.content());
        }
        ThinkTagStreamSplitter.Slice tail = splitter.finish();
        reasoning.append(tail.reasoning());
        content.append(tail.content());

        assertThat(reasoning.toString()).isEqualTo("思考中");
        assertThat(content.toString()).isEqualTo("前言正文继续");
    }

    @Test
    void feed_passesPlainTextThrough() {
        ThinkTagStreamSplitter splitter = new ThinkTagStreamSplitter();
        ThinkTagStreamSplitter.Slice slice = splitter.feed("没有思考标签的普通输出");
        assertThat(slice.reasoning()).isEmpty();
        assertThat(slice.content()).isEqualTo("没有思考标签的普通输出");
        assertThat(splitter.finish().isEmpty()).isTrue();
    }

    @Test
    void finish_flushesUnclosedThink() {
        ThinkTagStreamSplitter splitter = new ThinkTagStreamSplitter();
        ThinkTagStreamSplitter.Slice slice = splitter.feed("<think>只有思考没有闭合");
        ThinkTagStreamSplitter.Slice tail = splitter.finish();
        assertThat(slice.reasoning() + tail.reasoning()).isEqualTo("只有思考没有闭合");
        assertThat(slice.content() + tail.content()).isEmpty();
    }

    @Test
    void feed_keepsHalfTagLookalikeSafe() {
        ThinkTagStreamSplitter splitter = new ThinkTagStreamSplitter();
        // "<th" 疑似半个标签被滞留；下一块揭示它只是普通文本
        ThinkTagStreamSplitter.Slice first = splitter.feed("温度<th");
        assertThat(first.content()).isEqualTo("温度");
        ThinkTagStreamSplitter.Slice second = splitter.feed("e end>");
        assertThat(second.content()).isEqualTo("<the end>");
    }
}
