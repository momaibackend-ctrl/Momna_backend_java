package com.momna.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.foundation.api.AccountCenterController;
import com.momna.foundation.api.FoundationController;
import com.momna.modules.auth.api.AuthController;
import com.momna.modules.billing.api.BillingController;
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
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiContractParityTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
        FoundationController.class,
        AuthController.class,
        BillingController.class,
        ProfileController.class,
        LifecycleController.class,
        FlowController.class,
        AccountCenterController.class,
        RouterConfirmationController.class
    );

    @Test
    void canonicalOpenApiPathsAreImplemented() throws Exception {
        var expected = openApiPaths(Path.of("openapi.json"));
        var actual = controllerPaths();
        assertTrue(
            actual.containsAll(expected),
            () -> "Missing canonical API paths: "
                + difference(expected, actual)
        );
    }

    @Test
    void onboardingCompatibilityPathsAreImplemented()
        throws Exception {
        var expected = openApiPaths(
            Path.of("openapi-onboarding.json")
        );
        var actual = controllerPaths();
        assertTrue(
            actual.containsAll(expected),
            () -> "Missing onboarding API paths: "
                + difference(expected, actual)
        );
    }

    private Set<String> openApiPaths(Path path)
        throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.readTree(Files.readString(path));
        var fields = root.path("paths").fieldNames();
        var result = new LinkedHashSet<String>();
        fields.forEachRemaining(result::add);
        return result;
    }

    private Set<String> controllerPaths() {
        var result = new LinkedHashSet<String>();
        for (var controller : CONTROLLERS) {
            var prefix = requestPrefix(controller);
            for (var method : controller.getDeclaredMethods()) {
                mappedPath(method).ifPresent(
                    path -> result.add(normalize(prefix + path))
                );
            }
        }
        return result;
    }

    private String requestPrefix(Class<?> controller) {
        var mapping = controller.getAnnotation(
            RequestMapping.class
        );
        if (mapping == null || mapping.value().length == 0) {
            return "";
        }
        return mapping.value()[0];
    }

    private java.util.Optional<String> mappedPath(
        Method method
    ) {
        var get = method.getAnnotation(GetMapping.class);
        if (get != null) return java.util.Optional.of(
            first(get.value())
        );

        var post = method.getAnnotation(PostMapping.class);
        if (post != null) return java.util.Optional.of(
            first(post.value())
        );

        var put = method.getAnnotation(PutMapping.class);
        if (put != null) return java.util.Optional.of(
            first(put.value())
        );

        var patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) return java.util.Optional.of(
            first(patch.value())
        );

        var delete = method.getAnnotation(
            DeleteMapping.class
        );
        if (delete != null) return java.util.Optional.of(
            first(delete.value())
        );

        return java.util.Optional.empty();
    }

    private String first(String[] value) {
        return value.length == 0 ? "" : value[0];
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "/";
        var normalized = value.replaceAll("//+", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (
            normalized.length() > 1
                && normalized.endsWith("/")
        ) {
            normalized = normalized.substring(
                0,
                normalized.length() - 1
            );
        }
        return normalized;
    }

    private Set<String> difference(
        Set<String> expected,
        Set<String> actual
    ) {
        var missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
