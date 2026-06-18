package com.knowledgegame.core.infrastructure.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Markdown 渲染器（CommonMark + jsoup XSS 消毒）
 */
@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                StrikethroughExtension.create()
        );
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .build();
        this.safelist = Safelist.relaxed()
                .addAttributes(":all", "class")
                .addTags("details", "summary", "del", "s")
                .addAttributes("code", "class");
    }

    /**
     * 将 Markdown 渲染为安全的 HTML
     *
     * @param markdown Markdown 源码
     * @return 消毒后的 HTML；输入为 blank 时返回空字符串
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String html = renderer.render(parser.parse(markdown));
        return Jsoup.clean(html, safelist);
    }
}
