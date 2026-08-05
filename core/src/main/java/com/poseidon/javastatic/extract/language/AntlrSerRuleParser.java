package com.poseidon.javastatic.extract.language;

import com.poseidon.javastatic.extract.build.BuildAction;
import com.poseidon.javastatic.extract.build.BuildActionKind;
import com.poseidon.javastatic.extract.build.BuildExpression;
import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.build.NormalizeKind;
import com.poseidon.javastatic.extract.language.antlr.SerBaseVisitor;
import com.poseidon.javastatic.extract.language.antlr.SerLexer;
import com.poseidon.javastatic.extract.language.antlr.SerParser;
import com.poseidon.javastatic.extract.rule.EndpointSpec;
import com.poseidon.javastatic.extract.rule.FactSpec;
import com.poseidon.javastatic.extract.rule.FindSpec;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.source.AnnotationSelector;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.LetSpec;
import com.poseidon.javastatic.extract.source.MethodSelector;
import com.poseidon.javastatic.extract.source.SourceSpec;
import com.poseidon.javastatic.extract.source.TakeKind;
import com.poseidon.javastatic.extract.source.TakeSpec;
import com.poseidon.javastatic.extract.trace.ExternalValueEntryRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import com.poseidon.javastatic.extract.trace.TraceMatchSpec;
import com.poseidon.javastatic.extract.trace.TraceTargetKind;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses SER skeleton into the Java extractor model. Free atoms after find/from/when/take are
 * interpreted here using Java vocabulary — not by the shared grammar.
 * Optional value-trace lives in the same file as {@code trace { ... }} after build.
 */
public class AntlrSerRuleParser implements SerRuleParser {

    @Override
    public StaticExtractRule parse(String source) {
        SerParser parser = parser(source);
        return new RuleBuilder().visitRuleFile(parser.ruleFile());
    }

    private SerParser parser(String source) {
        SerLexer lexer = new SerLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
        SerParser parser = new SerParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);
        return parser;
    }

    private static final class RuleBuilder extends SerBaseVisitor<Object> {

        @Override
        public StaticExtractRule visitRuleFile(SerParser.RuleFileContext ctx) {
            String name = unquote(ctx.ruleDecl().STRING().getText());
            RuleTarget target = ruleTarget(ctx.ruleTargetDecl());
            FindSpec find = interpretFind(atoms(ctx.findDecl().freeAtom()));
            find = applyWhenAtoms(find, ctx.whenDecl());
            List<LetSpec> lets = ctx.letDecl().stream().map(this::buildLet).toList();
            BuildSpec build = buildBuild(ctx.buildDecl());
            StaticTraceRuleSet embedded =
                    ctx.embeddedTrace() != null ? buildEmbeddedTrace(name, ctx.embeddedTrace()) : null;
            return new StaticExtractRule(
                    name,
                    null,
                    true,
                    100,
                    target.fact(),
                    target.classifiers(),
                    target.endpoint(),
                    find,
                    lets,
                    build,
                    embedded);
        }

        private StaticTraceRuleSet buildEmbeddedTrace(String ruleName, SerParser.EmbeddedTraceContext ctx) {
            List<ExternalValueEntryRule> entries = new ArrayList<>();
            for (SerParser.TraceEntryContext entry : ctx.traceEntry()) {
                TraceTargetKind target = traceTarget(atoms(entry.freeAtom()));
                TraceMatchSpec match = traceMatch(entry.whenDecl());
                List<LetSpec> lets = entry.letDecl().stream().map(this::buildLet).toList();
                BuildSpec build = buildBuild(entry.buildDecl());
                entries.add(new ExternalValueEntryRule(target, match, lets, build));
            }
            return new StaticTraceRuleSet(ruleName + "#trace", entries);
        }

        private RuleTarget ruleTarget(SerParser.RuleTargetDeclContext ctx) {
            if (ctx.ENDPOINT() != null) {
                EndpointSpec endpoint =
                        new EndpointSpec(atoms(ctx.freeAtom()).get(0), atoms(ctx.freeAtom()).get(1));
                return new RuleTarget(
                        endpointFact(endpoint),
                        Map.of("category", endpoint.type(), "direction", endpoint.direction()),
                        endpoint);
            }
            FactSpec fact = new FactSpec(atoms(ctx.freeAtom()).get(0));
            return new RuleTarget(fact, Map.of(), new EndpointSpec(fact.type(), "fact"));
        }

        private FactSpec endpointFact(EndpointSpec endpoint) {
            return new FactSpec(endpoint.type().toLowerCase(Locale.ROOT) + "_" + endpoint.direction().toLowerCase(Locale.ROOT));
        }

        private FindSpec interpretFind(List<String> atoms) {
            if (atoms.isEmpty()) {
                throw new IllegalArgumentException("find requires vocabulary atoms");
            }
            String kind = atoms.get(0);
            String selector = atoms.size() > 1 ? atoms.get(1) : null;

            // freeIdent[...] may parse as one atom: call[get,post] or argument[0]
            if (kind.contains("[") && kind.endsWith("]")) {
                int bracket = kind.indexOf('[');
                selector = kind.substring(bracket);
                kind = kind.substring(0, bracket);
            }

            if ("call".equals(kind) && selector != null && selector.contains(".") && !selector.startsWith("[")) {
                return new FindSpec(JavaElementKind.CALL, null, null, methodSelectorFromText(selector));
            }

            JavaElementKind javaKind = javaKind(kind);
            if (javaKind == JavaElementKind.FIELD) {
                return new FindSpec(JavaElementKind.FIELD, selector, null, null);
            }
            if (javaKind == JavaElementKind.CLASS) {
                return new FindSpec(JavaElementKind.CLASS, null, null, null);
            }
            if (javaKind == JavaElementKind.METHOD) {
                return new FindSpec(JavaElementKind.METHOD, selector, null, null);
            }
            if (javaKind == JavaElementKind.CALL) {
                if (selector != null && selector.contains(".") && !selector.startsWith("[")) {
                    return new FindSpec(JavaElementKind.CALL, null, null, methodSelectorFromText(selector));
                }
                return new FindSpec(JavaElementKind.CALL, selector, null, null);
            }
            return new FindSpec(null, kind, selector, null, null);
        }

        private FindSpec applyWhenAtoms(FindSpec find, List<SerParser.WhenDeclContext> whens) {
            if (find == null || whens == null || whens.isEmpty()) {
                return find;
            }
            AnnotationSelector annotation = find.annotation();
            JavaElementKind onElement = null;
            for (SerParser.WhenDeclContext w : whens) {
                if (w.IF() != null) {
                    continue;
                }
                List<String> a = atoms(w.freeAtom());
                // annotation @X on method
                if (a.size() >= 4 && "annotation".equals(a.get(0)) && "on".equals(a.get(2))) {
                    onElement = javaKind(a.get(3));
                    annotation = annotationFromText(onElement, a.get(1));
                } else if (a.size() >= 2 && "annotation".equals(a.get(0)) && a.get(1).startsWith("@")) {
                    onElement = find.target() != null ? find.target() : JavaElementKind.METHOD;
                    annotation = annotationFromText(onElement, a.get(1));
                }
            }
            if (annotation == null) {
                return resolveGenericTarget(find);
            }
            JavaElementKind target = find.target() != null ? find.target() : onElement;
            if (target == null) {
                target = javaKind(find.targetKind());
            }
            return new FindSpec(target, find.name(), annotation, find.method());
        }

        private FindSpec resolveGenericTarget(FindSpec find) {
            if (find.target() != null || find.targetKind() == null) {
                return find;
            }
            JavaElementKind kind = javaKind(find.targetKind());
            if (kind == null) {
                return find;
            }
            return new FindSpec(kind, find.name(), find.annotation(), find.method());
        }

        private TraceTargetKind traceTarget(List<String> atoms) {
            if (atoms.isEmpty()) {
                throw new IllegalArgumentException("trace from requires a target");
            }
            return switch (atoms.get(0).toLowerCase(Locale.ROOT)) {
                case "field" -> TraceTargetKind.FIELD;
                case "call" -> TraceTargetKind.METHOD_CALL;
                case "parameter" -> TraceTargetKind.PARAMETER;
                case "method" -> TraceTargetKind.METHOD;
                case "return" -> TraceTargetKind.RETURN;
                case "assignment" -> TraceTargetKind.ASSIGNMENT;
                default -> throw new IllegalArgumentException("Unsupported trace target: " + atoms.get(0));
            };
        }

        private TraceMatchSpec traceMatch(List<SerParser.WhenDeclContext> conditions) {
            AnnotationSelector annotation = null;
            MethodSelector method = null;
            String fieldName = null;
            String fieldType = null;
            String parameterName = null;
            String parameterType = null;
            String methodName = null;
            String callName = null;
            String callOwner = null;
            String assignmentField = null;

            for (SerParser.WhenDeclContext condition : conditions) {
                if (condition.IF() != null) {
                    continue;
                }
                List<String> a = atoms(condition.freeAtom());
                if (a.isEmpty()) {
                    continue;
                }
                String head = a.get(0);
                if ("annotation".equals(head) && a.size() >= 4 && "on".equals(a.get(2))) {
                    JavaElementKind on = javaKind(a.get(3));
                    annotation = annotationFromText(on, a.get(1));
                } else if ("method".equals(head) && a.size() >= 2 && a.get(1).contains(".")) {
                    method = methodSelectorFromText(a.get(1));
                } else if ("call".equals(head) && a.size() >= 2 && a.get(1).contains(".")) {
                    method = methodSelectorFromText(a.get(1));
                } else if ("field".equals(head) && a.size() >= 3 && "name".equals(a.get(1))) {
                    fieldName = a.get(2);
                } else if ("field".equals(head) && a.size() >= 3 && "type".equals(a.get(1))) {
                    fieldType = a.get(2);
                } else if ("parameter".equals(head) && a.size() >= 3 && "name".equals(a.get(1))) {
                    parameterName = a.get(2);
                } else if ("parameter".equals(head) && a.size() >= 3 && "type".equals(a.get(1))) {
                    parameterType = a.get(2);
                } else if ("method".equals(head) && a.size() >= 3 && "name".equals(a.get(1))) {
                    methodName = a.get(2);
                } else if ("call".equals(head) && a.size() >= 3 && "name".equals(a.get(1))) {
                    callName = a.get(2);
                } else if ("call".equals(head) && a.size() >= 3 && "owner".equals(a.get(1))) {
                    callOwner = a.get(2);
                } else if ("assignment".equals(head) && a.size() >= 3 && "field".equals(a.get(1))) {
                    assignmentField = a.get(2);
                } else if ("call".equals(head) && a.size() >= 2) {
                    // when call config.get
                    String sel = a.get(1);
                    if (sel.contains(".")) {
                        method = methodSelectorFromText(sel);
                        int dot = sel.lastIndexOf('.');
                        callOwner = sel.substring(0, dot);
                        callName = sel.substring(dot + 1);
                    } else {
                        callName = sel;
                    }
                }
            }
            return new TraceMatchSpec(
                    annotation,
                    method,
                    fieldName,
                    fieldType,
                    parameterName,
                    parameterType,
                    methodName,
                    callName,
                    callOwner,
                    assignmentField);
        }

        private LetSpec buildLet(SerParser.LetDeclContext ctx) {
            String name = ctx.freeAtom().getText();
            List<SourceSpec> sources = ctx.sourceLine().stream().map(this::buildSource).toList();
            String defaultValue =
                    ctx.defaultLine() != null ? defaultLiteral(List.of(ctx.defaultLine().freeAtom().getText())) : null;
            Map<String, String> mapping = ctx.mapBlock() != null ? mapBlock(ctx.mapBlock()) : Map.of();
            List<BuildAction> pipeline =
                    ctx.pipelineStep() == null
                            ? List.of()
                            : ctx.pipelineStep().stream().map(this::buildAction).toList();
            return new LetSpec(name, sources, defaultValue, mapping, pipeline);
        }

        private SourceSpec buildSource(SerParser.SourceLineContext ctx) {
            List<String> from = atoms(ctx.freeAtom());
            // sourceLine: FROM freeAtom+ TAKE freeAtom+ — both lists merged in freeAtom() 
            // Need separate from and take — grammar has FROM freeAtom+ TAKE freeAtom+
            // In ANTLR, ctx.freeAtom() returns all free atoms in the rule. We need to split by TAKE.
            return buildSourceSplit(ctx);
        }

        private SourceSpec buildSourceSplit(SerParser.SourceLineContext ctx) {
            // Reconstruct from children order
            List<String> fromAtoms = new ArrayList<>();
            List<String> takeAtoms = new ArrayList<>();
            boolean inTake = false;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                var child = ctx.getChild(i);
                String text = child.getText();
                if ("from".equals(text)) {
                    inTake = false;
                    continue;
                }
                if ("take".equals(text)) {
                    inTake = true;
                    continue;
                }
                if (child instanceof SerParser.FreeAtomContext free) {
                    if (inTake) {
                        takeAtoms.add(free.getText());
                    } else {
                        fromAtoms.add(free.getText());
                    }
                }
            }
            TakeSpec take = interpretTake(takeAtoms);
            return interpretFrom(fromAtoms, take);
        }

        private SourceSpec interpretFrom(List<String> from, TakeSpec take) {
            if (from.isEmpty()) {
                throw new IllegalArgumentException("from requires vocabulary atoms");
            }
            String head = from.get(0);
            if ("annotation".equals(head) && from.size() >= 4 && "on".equals(from.get(2))) {
                JavaElementKind on = javaKind(from.get(3));
                AnnotationSelector ann = annotationFromText(on, from.get(1));
                return new SourceSpec(JavaElementKind.ANNOTATION, on, null, null, ann, null, null, take);
            }
            if ("annotation".equals(head) && from.size() >= 2 && from.get(1).startsWith("@")) {
                JavaElementKind on = from.size() >= 4 && "on".equals(from.get(2)) ? javaKind(from.get(3)) : JavaElementKind.METHOD;
                AnnotationSelector ann = annotationFromText(on, from.get(1));
                return new SourceSpec(JavaElementKind.ANNOTATION, on, null, null, ann, null, null, take);
            }
            if (head.startsWith("argument[") && head.endsWith("]")) {
                String inner = head.substring("argument[".length(), head.length() - 1);
                return new SourceSpec(
                        JavaElementKind.ARGUMENT, null, null, null, null, null, Integer.parseInt(inner), take);
            }
            if ("new".equals(head) && from.size() >= 2) {
                return new SourceSpec(JavaElementKind.NEW, null, from.get(1), null, null, null, null, take);
            }
            if ("literal".equals(head) && from.size() >= 2) {
                String lit = from.get(1);
                if (lit.startsWith("\"") && lit.endsWith("\"")) {
                    lit = unquote(lit);
                }
                return new SourceSpec(JavaElementKind.LITERAL, null, null, lit, null, null, null, take);
            }
            JavaElementKind kind = javaKind(head);
            String name = from.size() > 1 ? from.get(1) : null;
            if (kind != null) {
                return new SourceSpec(kind, null, name, null, null, null, null, take);
            }
            return new SourceSpec(null, head, null, null, name, null, null, null, null, take);
        }

        private TakeSpec interpretTake(List<String> take) {
            if (take.isEmpty()) {
                return new TakeSpec(TakeKind.VALUE, List.of());
            }
            String head = take.get(0);
            if ("attr".equals(head) || head.startsWith("attr(")) {
                String joined = String.join("", take);
                if (joined.startsWith("attr(") && joined.endsWith(")")) {
                    String inner = joined.substring(5, joined.length() - 1);
                    List<String> attrs = List.of(inner.split(","));
                    return new TakeSpec(TakeKind.ATTRIBUTE, attrs.stream().map(String::trim).toList());
                }
                if (take.size() >= 2 && take.get(1).startsWith("(")) {
                    String inner = take.get(1);
                    if (inner.startsWith("(") && inner.endsWith(")")) {
                        inner = inner.substring(1, inner.length() - 1);
                    }
                    return new TakeSpec(TakeKind.ATTRIBUTE, List.of(inner.split(",")).stream().map(String::trim).toList());
                }
            }
            return switch (head) {
                case "name" -> new TakeSpec(TakeKind.NAME, List.of());
                case "value" -> new TakeSpec(TakeKind.VALUE, List.of());
                case "raw" -> new TakeSpec(TakeKind.RAW, List.of());
                case "type" -> new TakeSpec(TakeKind.TYPE, List.of());
                case "owner" -> new TakeSpec(TakeKind.OWNER, List.of());
                case "signature" -> new TakeSpec(TakeKind.SIGNATURE, List.of());
                case "text", "reference", "callee", "method", "path", "dir", "extension" ->
                        new TakeSpec(null, head, List.of());
                default -> new TakeSpec(null, head, List.of());
            };
        }

        private BuildSpec buildBuild(SerParser.BuildDeclContext ctx) {
            Map<String, BuildExpression> fields = new LinkedHashMap<>();
            for (SerParser.BuildFieldContext field : ctx.buildField()) {
                String fieldName = field.freeAtom().getText();
                fields.put(fieldName, buildExpression(field));
            }
            return new BuildSpec(fields);
        }

        private BuildExpression buildExpression(SerParser.BuildFieldContext field) {
            SerParser.BuildExprContext expr = field.buildExpr();
            String reference = null;
            String constValue = null;
            List<String> concat = null;
            if (expr.STRING() != null) {
                constValue = unquote(expr.STRING().getText());
            } else if (expr.CONCAT() != null) {
                concat = expr.concatList().concatItem().stream()
                        .map(item -> item.STRING() != null ? unquote(item.STRING().getText()) : item.freeAtom().getText())
                        .toList();
            } else if (expr.freeAtom() != null) {
                reference = expr.freeAtom().getText();
            }
            List<BuildAction> actions = new ArrayList<>();
            for (SerParser.PipelineStepContext step : field.pipelineStep()) {
                actions.add(buildAction(step));
            }
            return new BuildExpression(reference, constValue, concat, actions);
        }

        private BuildAction buildAction(SerParser.PipelineStepContext step) {
            if (step.NORMALIZE() != null) {
                return new BuildAction(
                        BuildActionKind.NORMALIZE, null, null, null, normalize(step.IDENT().getText()), null);
            }
            if (step.REGEX() != null) {
                return new BuildAction(
                        BuildActionKind.REGEX,
                        unquote(step.STRING(0).getText()),
                        Integer.parseInt(step.INT().getText()),
                        null,
                        null,
                        null);
            }
            if (step.REPLACE() != null) {
                return new BuildAction(
                        BuildActionKind.REPLACE,
                        unquote(step.STRING(0).getText()),
                        null,
                        unquote(step.STRING(1).getText()),
                        null,
                        null);
            }
            return new BuildAction(BuildActionKind.MAP, null, null, null, null, mapBlockEntries(step.mapEntry()));
        }

        private Map<String, String> mapBlock(SerParser.MapBlockContext ctx) {
            return mapBlockEntries(ctx.mapEntry());
        }

        private Map<String, String> mapBlockEntries(List<SerParser.MapEntryContext> entries) {
            Map<String, String> out = new LinkedHashMap<>();
            for (SerParser.MapEntryContext entry : entries) {
                out.put(entry.freeAtom(0).getText(), entry.freeAtom(1).getText());
            }
            return out;
        }

        private MethodSelector methodSelectorFromText(String selector) {
            // Owner.name or Owner.[a,b]
            if (selector.contains(".[")) {
                int idx = selector.indexOf(".[");
                String owner = selector.substring(0, idx);
                String inner = selector.substring(idx + 2, selector.length() - 1);
                List<String> names = List.of(inner.split(",")).stream().map(String::trim).toList();
                return new MethodSelector(owner, null, names, null);
            }
            int dot = selector.lastIndexOf('.');
            if (dot <= 0) {
                return new MethodSelector(null, null, List.of(selector), null);
            }
            return new MethodSelector(selector.substring(0, dot), null, List.of(selector.substring(dot + 1)), null);
        }

        private AnnotationSelector annotationFromText(JavaElementKind on, String ref) {
            String text = ref.startsWith("@") ? ref.substring(1) : ref;
            if (text.startsWith("*")) {
                return new AnnotationSelector(on, List.of(), ".*" + text.substring(1));
            }
            return new AnnotationSelector(on, List.of(text), null);
        }

        private static List<String> atoms(List<SerParser.FreeAtomContext> ctxs) {
            return ctxs.stream().map(c -> c.getText()).collect(Collectors.toList());
        }

        private static List<String> atoms(SerParser.FreeAtomContext ctx) {
            return List.of(ctx.getText());
        }

        private static String defaultLiteral(List<String> atoms) {
            if (atoms.isEmpty()) {
                return null;
            }
            String v = atoms.get(0);
            if (v.startsWith("\"") && v.endsWith("\"")) {
                return unquote(v);
            }
            return v;
        }

        private static JavaElementKind javaKind(String kind) {
            if (kind == null) {
                return null;
            }
            return switch (kind.toLowerCase(Locale.ROOT)) {
                case "method" -> JavaElementKind.METHOD;
                case "class" -> JavaElementKind.CLASS;
                case "field" -> JavaElementKind.FIELD;
                case "parameter" -> JavaElementKind.PARAMETER;
                case "call" -> JavaElementKind.CALL;
                case "return" -> JavaElementKind.RETURN;
                case "assignment" -> JavaElementKind.ASSIGNMENT;
                case "argument" -> JavaElementKind.ARGUMENT;
                case "literal" -> JavaElementKind.LITERAL;
                case "new" -> JavaElementKind.NEW;
                case "annotation" -> JavaElementKind.ANNOTATION;
                default -> null;
            };
        }

        private NormalizeKind normalize(String raw) {
            String v = raw.toLowerCase(Locale.ROOT);
            return switch (v) {
                case "slash" -> NormalizeKind.SLASH;
                case "pathvariable", "path_variable" -> NormalizeKind.PATH_VARIABLE;
                case "extractpath", "extract_path" -> NormalizeKind.EXTRACT_PATH;
                case "placeholderlookup", "placeholder_lookup" -> NormalizeKind.PLACEHOLDER_LOOKUP;
                case "placeholderdefault", "placeholder_default" -> NormalizeKind.PLACEHOLDER_DEFAULT;
                case "kebab", "kebabcase", "kebab_case" -> NormalizeKind.KEBAB;
                default -> throw new IllegalArgumentException("Unsupported normalize kind: " + raw);
            };
        }

        private static String unquote(String s) {
            String body = s.substring(1, s.length() - 1);
            return body.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }

    private record RuleTarget(FactSpec fact, Map<String, String> classifiers, EndpointSpec endpoint) {}

    private static final class ThrowingErrorListener extends BaseErrorListener {
        private static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
            throw new IllegalArgumentException(
                    "Invalid SER syntax at line " + line + ", column " + charPositionInLine + ": " + msg, e);
        }
    }
}
