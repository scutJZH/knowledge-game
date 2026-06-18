package com.knowledgegame.core.infrastructure.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownRenderer 单元测试
 */
class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    /**
     * 渲染 - 空字符串返回 ""
     */
    @Test
    void render_shouldReturnEmpty_whenBlank() {
        assertEquals("", renderer.render(null));
        assertEquals("", renderer.render(""));
        assertEquals("", renderer.render("   "));
    }

    /**
     * 渲染 - 标题
     */
    @Test
    void render_shouldRenderHeading() {
        String html = renderer.render("# 标题");

        assertTrue(html.contains("<h1>标题</h1>"));
    }

    /**
     * 渲染 - 无序列表
     */
    @Test
    void render_shouldRenderUnorderedList() {
        String html = renderer.render("- 条目1\n- 条目2");

        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>条目1</li>"));
        assertTrue(html.contains("<li>条目2</li>"));
    }

    /**
     * 渲染 - 代码块
     */
    @Test
    void render_shouldRenderCodeBlock() {
        String html = renderer.render("```java\nint x = 1;\n```");

        assertTrue(html.contains("<code"));
        assertTrue(html.contains("int x = 1"));
    }

    /**
     * 渲染 - 链接
     */
    @Test
    void render_shouldRenderLink() {
        String html = renderer.render("[链接](https://example.com)");

        assertTrue(html.contains("<a href=\"https://example.com\">链接</a>"));
    }

    /**
     * 渲染 - GFM 表格
     */
    @Test
    void render_shouldRenderTable() {
        String html = renderer.render("| A | B |\n|---|---|\n| 1 | 2 |");

        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<th>A</th>"));
        assertTrue(html.contains("<td>1</td>"));
    }

    /**
     * 渲染 - GFM 删除线
     */
    @Test
    void render_shouldRenderStrikethrough() {
        String html = renderer.render("~~删除~~");

        assertTrue(html.contains("<del>删除</del>") || html.contains("<s>删除</s>"));
    }

    /**
     * XSS - script 标签被剥离
     */
    @Test
    void render_shouldStripScriptTag() {
        String html = renderer.render("<script>alert('xss')</script>");

        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("alert"));
    }

    /**
     * XSS - onclick 属性被剥离
     */
    @Test
    void render_shouldStripOnclickAttribute() {
        String html = renderer.render("<a onclick=\"alert(1)\">点击</a>");

        assertFalse(html.contains("onclick"));
    }

    /**
     * XSS - javascript: 协议被剥离
     */
    @Test
    void render_shouldStripJavascriptProtocol() {
        String html = renderer.render("[链接](javascript:alert(1))");

        assertFalse(html.contains("javascript"));
    }

    /**
     * XSS - iframe 被剥离
     */
    @Test
    void render_shouldStripIframe() {
        String html = renderer.render("<iframe src=\"https://evil.com\"></iframe>");

        assertFalse(html.contains("<iframe"));
    }

    /**
     * 保留 - code 的 class 属性（语言标注）
     */
    @Test
    void render_shouldPreserveCodeClass() {
        String html = renderer.render("```python\nprint('hello')\n```");

        assertTrue(html.contains("python") || html.contains("language"));
    }

}
