package com.cyan.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 白名单过滤器 —— 防范 XSS 攻击
 * 使用 Jsoup 的 Safelist 仅保留安全标签和属性
 */
public class XssFilter {

    private static final Safelist SAFELIST = Safelist.relaxed()
            // 允许的标签：富文本常见标签（加粗斜体下划线删除线字体颜色背景色对齐列表链接图片）
            .addTags(
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "br", "hr", "div", "span",
                    "ul", "ol", "li",
                    "strong", "em", "u", "s", "strike", "b", "i",
                    "font", "a", "img", "pre", "code", "blockquote",
                    "table", "thead", "tbody", "tr", "td", "th", "caption"
            )
            // 字体颜色/背景色/字号
            .addAttributes("font", "color", "face", "size")
            // 对齐/宽高
            .addAttributes("p", "align")
            .addAttributes("div", "align")
            .addAttributes("span", "style")
            .addAttributes("font", "style")
            // 链接安全限制
            .addAttributes("a", "href", "title", "target", "rel")
            // 图片安全限制
            .addAttributes("img", "src", "alt", "title", "width", "height", "style")
            // 表格
            .addAttributes("table", "border", "cellpadding", "cellspacing", "width", "align")
            .addAttributes("td", "colspan", "rowspan", "align", "width")
            .addAttributes("th", "colspan", "rowspan", "align", "width")
            // 全局允许的 style 属性（过滤 inline style 中的 expression/javascript）
            .addAttributes(":all", "style")
            // 协议白名单：http, https, mailto, data:image（图片 base64）
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            // 禁止危险属性（on开头事件、script、iframe等标签已被默认移除）
            .removeTags("script", "iframe", "object", "embed", "form", "input", "textarea", "button", "link", "meta", "style");

    /**
     * 过滤 HTML 字符串，移除所有危险标签与属性
     * @param html 原始 HTML（可能含 script / on事件等）
     * @return 过滤后的安全 HTML；输入 null/空返回原串
     */
    public static String clean(String html) {
        if (html == null || html.isEmpty()) return html;
        // 第一步：Jsoup safelist 白名单过滤
        String safe = Jsoup.clean(html, SAFELIST);
        // 第二步：二次兜底 —— 用正则移除 style 里可能残留的 expression / javascript / vbscript
        safe = safe.replaceAll("(?is)expression\\s*\\(", "");
        safe = safe.replaceAll("(?is)javascript\\s*:", "");
        safe = safe.replaceAll("(?is)vbscript\\s*:", "");
        return safe;
    }

    /** 判空便利方法 */
    public static boolean isClean(String html) {
        if (html == null || html.isEmpty()) return true;
        return Jsoup.isValid(html, SAFELIST);
    }
}
