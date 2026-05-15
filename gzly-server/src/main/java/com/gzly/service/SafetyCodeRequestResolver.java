package com.gzly.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Service
public class SafetyCodeRequestResolver {

    public String resolve(HttpServletRequest request) {
        return resolve(request, null);
    }

    public String resolve(HttpServletRequest request, Object body) {
        return firstNonBlank(
                header(request, "X-Safety-Code"),
                header(request, "X-Plan-Safety-Code"),
                header(request, "X-Access-Key"),
                header(request, "X-Plan-Access-Key"),
                parameter(request, "safetyCode"),
                parameter(request, "accessKey"),
                bodyValue(body, "safetyCode"),
                bodyValue(body, "accessKey"));
    }

    public String resolve(Object body) {
        return firstNonBlank(bodyValue(body, "safetyCode"), bodyValue(body, "accessKey"));
    }

    public String resolve(String safetyCode, String accessKey) {
        return firstNonBlank(safetyCode, accessKey);
    }

    public String mask(String rawCode) {
        String value = safeTrim(rawCode);
        if (value.isBlank()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? "" : safeTrim(request.getHeader(name));
    }

    private String parameter(HttpServletRequest request, String name) {
        return request == null ? "" : safeTrim(request.getParameter(name));
    }

    private String bodyValue(Object body, String fieldName) {
        if (body == null) {
            return "";
        }
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            Method method = body.getClass().getMethod(getterName);
            Object value = method.invoke(body);
            return value == null ? "" : safeTrim(String.valueOf(value));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = body.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(body);
            return value == null ? "" : safeTrim(String.valueOf(value));
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = safeTrim(value);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
