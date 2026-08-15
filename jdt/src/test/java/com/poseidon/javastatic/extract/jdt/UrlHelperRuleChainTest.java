package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F6: uri(buildXxxUrl()) continues via helper find-call rules + field @Value / externalValues.
 */
class UrlHelperRuleChainTest {

    @Test
    void webClientUriChainsThroughBuildTemplatePageUrl() {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        StaticExtractRule webClient = parser.parse("""
                rule "WebClient HTTP Outbound"
                endpoint HTTP outbound

                find call WebClient.[get,post]

                let httpMethod = from call take name
                let path = from chain next uri argument[0] take value

                build {
                  httpMethod: httpMethod
                  path: path | normalize extractPath | normalize pathVariable
                }

                trace {
                  from field
                  when annotation @Value on field
                  let rawValue = from annotation @Value on field take attr(value)
                  build {
                    namespace: "config"
                    lookup: rawValue | normalize placeholderLookup
                    default: rawValue | normalize placeholderDefault
                  }
                }
                """);
        StaticExtractRule helper = parser.parse("""
                rule "url-helper buildTemplatePageUrl"
                fact url_helper

                find call buildTemplatePageUrl

                let path = from field templatePageUrl take value

                build {
                  path: path
                  value: path
                }

                trace {
                  from field
                  when annotation @Value on field
                  let rawValue = from annotation @Value on field take attr(value)
                  build {
                    namespace: "config"
                    lookup: rawValue | normalize placeholderLookup
                    default: rawValue | normalize placeholderDefault
                  }
                }
                """);

        CompilationUnit cu = parse("""
                package demo;
                import org.springframework.beans.factory.annotation.Value;

                class TemplateGateway {
                  @Value("${template.page-url}")
                  private String templatePageUrl;
                  private WebClient.Builder b;

                  void query() {
                    b.build().get().uri(buildTemplatePageUrl()).retrieve();
                  }
                  private String buildTemplatePageUrl() {
                    return templatePageUrl + "?pageNum=1";
                  }
                }
                class WebClient {
                  static class Builder {
                    WebClient build() { return new WebClient(); }
                  }
                  WebClient get() { return this; }
                  WebClient post() { return this; }
                  WebClient uri(String u) { return this; }
                  void retrieve() {}
                }
                """);
        // inject Value annotation type as simple source-level only — may not resolve
        TypeDeclaration type = typeNamed(cu, "TemplateGateway");

        MapExternalValueResolver ext = new MapExternalValueResolver(Map.of(
                "config", Map.of("template.page-url", List.of("/v1/template/page"))
        ));
        JdtTraceOptions opts = JdtTraceOptions.of(List.of(), ext);
        List<StaticExtractRule> rules = List.of(webClient, helper);
        DefaultJdtStaticExtractEngine engine = new DefaultJdtStaticExtractEngine(opts, rules);

        List<StaticExtractResult> results = engine.execute(webClient, cu, type, "TemplateGateway.java", null);
        assertFalse(results.isEmpty(), "should extract at least one endpoint");
        String path = results.get(0).fields().get("path");
        System.out.println("EXTRACTED path=" + path + " fields=" + results.get(0).fields());
        assertNotNull(path);
        assertFalse(path.contains("buildTemplatePageUrl"), "must not keep method call as path: " + path);
        assertTrue(path.contains("/v1/template") || path.contains("template"),
                "expected resolved config path, got: " + path);
    }

    @Test
    void withoutHelperRule_keepsMethodCallText() {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        StaticExtractRule webClient = parser.parse("""
                rule "WebClient HTTP Outbound"
                endpoint HTTP outbound
                find call WebClient.[get,post]
                let path = from chain next uri argument[0] take value
                build { path: path }
                """);
        CompilationUnit cu = parse("""
                package demo;
                class TemplateGateway {
                  private WebClient.Builder b;
                  void query() {
                    b.build().get().uri(buildTemplatePageUrl()).retrieve();
                  }
                  private String buildTemplatePageUrl() { return "/v1/x"; }
                }
                class WebClient {
                  static class Builder { WebClient build() { return new WebClient(); } }
                  WebClient get() { return this; }
                  WebClient uri(String u) { return this; }
                  void retrieve() {}
                }
                """);
        TypeDeclaration type = typeNamed(cu, "TemplateGateway");
        DefaultJdtStaticExtractEngine engine = new DefaultJdtStaticExtractEngine(JdtTraceOptions.empty(), List.of(webClient));
        List<StaticExtractResult> results = engine.execute(webClient, cu, type, "T.java", null);
        assertFalse(results.isEmpty());
        String path = results.get(0).fields().get("path");
        System.out.println("WITHOUT helper path=" + path);
        assertTrue(path != null && path.contains("buildTemplatePageUrl"));
    }

    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Test.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(JavaCore.getOptions());
        return (CompilationUnit) parser.createAST(null);
    }

    private TypeDeclaration typeNamed(CompilationUnit cu, String name) {
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration declaration
                    && name.equals(declaration.getName().getIdentifier())) {
                return declaration;
            }
        }
        throw new IllegalArgumentException("Missing type: " + name);
    }
}
