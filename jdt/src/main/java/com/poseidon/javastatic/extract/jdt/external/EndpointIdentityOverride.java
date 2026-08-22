package com.poseidon.javastatic.extract.jdt.external;

import com.poseidon.javastatic.extract.jdt.trace.external.ExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.external.IdentityDictResolver;
import com.poseidon.javastatic.extract.rule.EndpointSpec;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Identity dictionary for SER endpoint facts (static-extract, not code-graph / not pine semantics).
 *
 * <p><b>User-facing dict (flat, no wrapper, no arrays, no project prefix):</b>
 * <pre>
 * {
 *   "com.foo.bar.KaleidoPublisher.send()": "cooper_domain_event_test"
 * }
 * </pre>
 *
 * <p>Key rule (Java): {@code {fqcn}.{method}()} — fully-qualified class name required.
 * Multi-hit in the same method: {@code ...method().1}, {@code ...method().2}.
 *
 * <p>Applied after SER build. HIT overwrites identity field and sets {@code parseLevel=config}.
 */
public final class EndpointIdentityOverride {

    /**
     * Internal namespace when identity is carried inside multi-namespace externalValues maps.
     * User JSON is flat and does not contain this key.
     */
    /** Internal wire namespace only; user JSON is flat and does not contain this key. */
    public static final String NAMESPACE = "identity";

    private EndpointIdentityOverride() {}

    /**
     * @param siteIndex 0 for first hit in method; {@code .1}, {@code .2} when &gt; 0
     */
    public static String methodKey(String fqcn, String methodName, int siteIndex) {
        String type = blankToUnknown(fqcn);
        String method = blankToUnknown(methodName);
        String base = type + "." + method + "()";
        if (siteIndex > 0) {
            return base + "." + siteIndex;
        }
        return base;
    }

    /** Resolve FQCN from type declaration (binding preferred). */
    public static String fqcnOf(TypeDeclaration typeDecl) {
        if (typeDecl == null) {
            return "unknown";
        }
        ITypeBinding binding = typeDecl.resolveBinding();
        if (binding != null && binding.getQualifiedName() != null && !binding.getQualifiedName().isBlank()) {
            return binding.getQualifiedName().trim();
        }
        String simple = typeDecl.getName().getIdentifier();
        if (typeDecl.getRoot() instanceof org.eclipse.jdt.core.dom.CompilationUnit cu
                && cu.getPackage() != null) {
            String pkg = cu.getPackage().getName().getFullyQualifiedName();
            if (pkg != null && !pkg.isBlank()) {
                return pkg + "." + simple;
            }
        }
        return simple;
    }

    public static String lookup(ExternalValueResolver resolver, String key) {
        if (resolver == null || key == null || key.isBlank()) {
            return null;
        }
        // Prefer dedicated identity API when available
        if (resolver instanceof IdentityDictResolver idr) {
            String v = idr.resolveIdentity(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        List<String> values = resolver.resolve(NAMESPACE, key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String v = values.get(0);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * Apply identity override to a built field map. Returns original map if MISS.
     */
    public static Map<String, String> apply(
            Map<String, String> fields,
            StaticExtractRule rule,
            TypeDeclaration typeDecl,
            ASTNode anchor,
            String projectName,
            ExternalValueResolver resolver,
            Map<String, Integer> methodHitIndex) {
        if (fields == null || fields.isEmpty() || typeDecl == null) {
            return fields;
        }
        if (rule == null || rule.endpoint() == null) {
            return fields;
        }

        // Prefer dict embedded in the SER rule; else external --external-values resolver.
        ExternalValueResolver effective = resolver;
        if (rule.identityDict() != null && !rule.identityDict().isEmpty()) {
            effective = com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver
                    .ofIdentity(rule.identityDict());
        }
        if (effective == null) {
            return fields;
        }

        String fqcn = fqcnOf(typeDecl);
        String methodName = enclosingMethodName(anchor);
        String methodScope = fqcn + "#" + methodName;
        int index = 0;
        if (methodHitIndex != null) {
            index = methodHitIndex.getOrDefault(methodScope, 0);
            methodHitIndex.put(methodScope, index + 1);
        }

        String key = methodKey(fqcn, methodName, index);
        String override = lookup(effective, key);
        if (override == null) {
            return fields;
        }

        String identityField = resolveIdentityField(rule, fields);
        if (identityField == null) {
            return fields;
        }

        String value = normalizeIdentity(override);
        Map<String, String> out = new LinkedHashMap<>(fields);
        out.put(identityField, value);
        out.put("parseLevel", "config");
        return out;
    }

    static String resolveIdentityField(StaticExtractRule rule, Map<String, String> fields) {
        EndpointSpec ep = rule != null ? rule.endpoint() : null;
        String type = ep != null && ep.type() != null ? ep.type().trim().toUpperCase(Locale.ROOT) : "";
        String preferred = switch (type) {
            case "HTTP" -> "path";
            case "MQ" -> "topic";
            case "REDIS" -> fields.containsKey("keyPattern") ? "keyPattern" : "key";
            case "DB" -> "tableName";
            case "UI" -> fields.containsKey("uiText") ? "uiText" : "routePath";
            default -> null;
        };
        if (preferred != null) {
            return preferred;
        }
        for (String name : List.of("path", "topic", "keyPattern", "key", "tableName", "uiText", "routePath")) {
            if (fields.containsKey(name)) {
                return name;
            }
        }
        return null;
    }

    private static String normalizeIdentity(String raw) {
        return raw.trim();
    }

    public static String enclosingMethodName(ASTNode anchor) {
        MethodDeclaration md = enclosingMethod(anchor);
        return md != null ? md.getName().getIdentifier() : "unknown";
    }

    public static MethodDeclaration enclosingMethod(ASTNode anchor) {
        if (anchor == null) {
            return null;
        }
        if (anchor instanceof MethodDeclaration md) {
            return md;
        }
        ASTNode p = anchor.getParent();
        while (p != null && !(p instanceof MethodDeclaration)) {
            p = p.getParent();
        }
        return p instanceof MethodDeclaration md ? md : null;
    }

    private static String blankToUnknown(String s) {
        return s == null || s.isBlank() ? "unknown" : s.trim();
    }
}
