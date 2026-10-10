package com.momna.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.foundation.api.AccountCenterController;
import com.momna.foundation.api.FoundationController;
import com.momna.modules.auth.api.AuthController;
import com.momna.modules.billing.api.BillingController;
import com.momna.modules.checkin.api.CheckinController;
import com.momna.modules.flow.api.FlowController;
import com.momna.modules.flow.api.RouterConfirmationController;
import com.momna.modules.lifecycle.api.LifecycleController;
import com.momna.modules.profile.api.ProfileController;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies METHOD + normalized URL across every versioned OpenAPI contract.
 * A path-only parity test cannot detect an absent safety route or the wrong HTTP verb.
 */
class CompleteApiOperationParityTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
        FoundationController.class, AuthController.class, BillingController.class,
        ProfileController.class, LifecycleController.class, FlowController.class,
        RouterConfirmationController.class, AccountCenterController.class,
        CheckinController.class
    );
    private static final List<String> CONTRACTS = List.of(
        "openapi.json",
        "openapi-onboarding.json",
        "openapi/onboarding-public-v1.json",
        "openapi/checkin-public-v1.yaml"
    );

    @Test
    void everyDocumentedOperationHasAMatchingJavaControllerMethod() throws Exception {
        var actual = controllerOperations();
        var missing = new LinkedHashSet<String>();
        for (var file : CONTRACTS) {
            var expected = file.endsWith(".yaml") ? yamlOperations(Path.of(file))
                : jsonOperations(Path.of(file));
            for (var operation : expected) {
                if (!actual.contains(operation)) {
                    missing.add(file + ": " + operation);
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> "Missing Java API operations:\n"
            + String.join("\n", missing));
    }

    private Set<String> jsonOperations(Path path) throws Exception {
        var root = new ObjectMapper().readTree(Files.readString(path));
        var result = new LinkedHashSet<String>();
        var paths = root.path("paths");
        var names = paths.fieldNames();
        while (names.hasNext()) {
            var name = names.next();
            var methods = paths.path(name).fieldNames();
            while (methods.hasNext()) {
                var method = methods.next().toUpperCase();
                if (isHttpMethod(method)) result.add(method + " " + normalize(name));
            }
        }
        return result;
    }

    // The checked-in check-in contract uses unindented OpenAPI path/method keys.
    private Set<String> yamlOperations(Path file) throws Exception {
        var result = new LinkedHashSet<String>();
        var pathLine = Pattern.compile("^  (/[^:]+):\\s*$");
        var methodLine = Pattern.compile("^    (get|post|put|patch|delete):\\s*$");
        String current = null;
        for (var line : Files.readAllLines(file)) {
            var path = pathLine.matcher(line);
            var method = methodLine.matcher(line);
            if (path.matches()) current = normalize(path.group(1));
            else if (current != null && method.matches())
                result.add(method.group(1).toUpperCase() + " " + current);
        }
        assertTrue(!result.isEmpty(), "Could not parse OpenAPI YAML operations");
        return result;
    }

    private Set<String> controllerOperations() {
        var result = new LinkedHashSet<String>();
        for (var controller : CONTROLLERS) {
            var prefix = controller.getAnnotation(RequestMapping.class);
            var base = prefix == null || prefix.value().length == 0 ? "" : prefix.value()[0];
            for (Method method : controller.getDeclaredMethods()) {
                add(result, "GET", base, method.getAnnotation(GetMapping.class));
                add(result, "POST", base, method.getAnnotation(PostMapping.class));
                add(result, "PUT", base, method.getAnnotation(PutMapping.class));
                add(result, "PATCH", base, method.getAnnotation(PatchMapping.class));
                add(result, "DELETE", base, method.getAnnotation(DeleteMapping.class));
            }
        }
        return result;
    }

    private void add(Set<String> result, String verb, String base, Object annotation) {
        if (annotation == null) return;
        String[] values;
        if (annotation instanceof GetMapping x) values = x.value();
        else if (annotation instanceof PostMapping x) values = x.value();
        else if (annotation instanceof PutMapping x) values = x.value();
        else if (annotation instanceof PatchMapping x) values = x.value();
        else if (annotation instanceof DeleteMapping x) values = x.value();
        else return;
        if (values.length == 0) result.add(verb + " " + normalize(base));
        else for (var value : values) result.add(verb + " " + normalize(base + value));
    }

    private boolean isHttpMethod(String verb) {
        return Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(verb);
    }

    private String normalize(String path) {
        var cleaned = path.replaceAll("//+", "/");
        if (!cleaned.startsWith("/")) cleaned = "/" + cleaned;
        if (cleaned.length() > 1 && cleaned.endsWith("/"))
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned.replaceAll("\\{[^/}]+}", "{}");
    }
}
