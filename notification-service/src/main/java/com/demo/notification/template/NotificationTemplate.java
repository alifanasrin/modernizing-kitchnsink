package com.demo.notification.template;

import com.demo.notification.event.NotificationEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A template for one {@code notificationType} on one {@code channel}: the {@code data} fields the producer must
 * supply and the texts with {@code {{placeholders}}}. A channel may omit the subject or HTML
 * (then they are null). Placeholders available: every {@code data} entry,
 * plus {@code name} (recipient name or "there"), {@code purpose} (default "verify your identity") and {@code minutes} (from {@code validForSeconds}).
 * Values are substituted in a single pass (never re-scanned) and HTML-escaped in the HTML body.
 */
public record NotificationTemplate(String type, String channel, List<DataField> fields, String subject, String text,
                                   String html) {

    /** The filled-in texts; {@code subject} and {@code html} are null when the channel has none. */
    public record Rendered(String subject, String text, String html) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public void validate(Map<String, Object> data) {
        fields.forEach(f -> f.validate(type, data));
    }

    public Rendered render(NotificationEvent event) {
        Map<String, String> vars = new HashMap<>();
        event.data().forEach((k, v) -> vars.put(k, String.valueOf(v)));
        String recipientName = event.recipient().name();
        vars.put("name", recipientName == null || recipientName.isBlank() ? "there" : recipientName);
        vars.putIfAbsent("purpose", "verify your identity");
        if (event.data().get("validForSeconds") instanceof Number n) {
            vars.put("minutes", String.valueOf(Math.max(1, n.longValue() / 60)));
        }
        return new Rendered(subject == null ? null : fill(subject, vars, false), fill(text, vars, false),
                html == null ? null : fill(html, vars, true));
    }

    static String fill(String template, Map<String, String> vars, boolean escapeHtml) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(escapeHtml ? escape(value) : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
