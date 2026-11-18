package com.demo.notification.template;

import com.demo.notification.exception.InvalidNotificationException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * One entry of a template's {@code data} map. Error messages name the field only, never its value
 * (values are codes and reset links).
 */
public record DataField(String name, boolean required, Kind kind, Pattern pattern) {

    public enum Kind { STRING, POSITIVE_INT }

    public static DataField string(String name, boolean required, String regex) {
        return new DataField(name, required, Kind.STRING, Pattern.compile(regex));
    }

    public static DataField positiveInt(String name) {
        return new DataField(name, true, Kind.POSITIVE_INT, null);
    }

    void validate(String templateType, Map<String, Object> data) {
        Object value = data.get(name);
        if (value == null) {
            if (required) {
                throw new InvalidNotificationException(templateType + " requires data." + name);
            }
            return;
        }
        boolean ok = switch (kind) {
            case STRING -> value instanceof String s && pattern.matcher(s).matches();
            case POSITIVE_INT -> value instanceof Number n && n.longValue() > 0;
        };
        if (!ok) {
            throw new InvalidNotificationException(templateType + " has an invalid data." + name);
        }
    }
}
