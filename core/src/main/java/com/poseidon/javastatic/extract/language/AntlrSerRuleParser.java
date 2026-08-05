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

public class AntlrSerRuleParser implements SerRuleParser, SerTraceRuleParser {

    @Override
    public StaticExtractRule parse(String source) {
        SerParser parser = parser(source);
        return new RuleBuilder().visitRuleFile(parser.ruleFile());
    }

    @Override
    public StaticTraceRuleSet parseTrace(String source) {
        SerParser parser = parser(source);
        return new RuleBuilder().visitTraceFile(parser.traceFile());
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
            FindSpec find = buildFind(ctx.findDecl());
            List<LetSpec> lets = ctx.letDecl().stream().map(this::buildLet).toList();
            BuildSpec build = buildBuild(ctx.buildDecl());
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
                    build);
        }

        private RuleTarget ruleTarget(SerParser.RuleTargetDeclContext ctx) {
            if (ctx.endpointDecl() != null) {
                EndpointSpec endpoint =
                        new EndpointSpec(
                                ctx.endpointDecl().valueToken(0).getText(),
                                ctx.endpointDecl().valueToken(1).getText());
                return new RuleTarget(
                        endpointFact(endpoint),
                        Map.of(
                                "category", endpoint.type(),
                                "direction", endpoint.direction()),
                        endpoint);
            }
            FactSpec fact = new FactSpec(ctx.factDecl().valueToken().getText());
            return new RuleTarget(fact, Map.of(), new EndpointSpec(fact.type(), "fact"));
        }

        private FactSpec endpointFact(EndpointSpec endpoint) {
            return new FactSpec(
                    endpoint.type().toLowerCase(Locale.ROOT)
                            + "_"
                            + endpoint.direction().toLowerCase(Locale.ROOT));
        }

        @Override
        public StaticTraceRuleSet visitTraceFile(SerParser.TraceFileContext ctx) {
            String name = unquote(ctx.traceDecl().STRING().getText());
            List<ExternalValueEntryRule> entries = ctx.traceEntry().stream().map(this::traceEntry).toList();
            return new StaticTraceRuleSet(name, entries);
        }

        private ExternalValueEntryRule traceEntry(SerParser.TraceEntryContext ctx) {
            return new ExternalValueEntryRule(
                    traceTarget(ctx.traceTarget()),
                    traceMatch(ctx.whenDecl()),
                    ctx.letDecl().stream().map(this::buildLet).toList(),
                    buildBuild(ctx.buildDecl()));
        }

        private TraceTargetKind traceTarget(SerParser.TraceTargetContext ctx) {
            if (ctx.FIELD() != null) {
                return TraceTargetKind.FIELD;
            }
            if (ctx.CALL() != null) {
                return TraceTargetKind.METHOD_CALL;
            }
            if (ctx.PARAMETER() != null) {
                return TraceTargetKind.PARAMETER;
            }
            if (ctx.METHOD() != null) {
                return TraceTargetKind.METHOD;
            }
            if (ctx.RETURN() != null) {
                return TraceTargetKind.RETURN;
            }
            return TraceTargetKind.ASSIGNMENT;
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
                if (condition.annotationRef() != null) {
                    annotation = annotation(element(condition.elementRef()), condition.annotationRef());
                } else if (condition.methodPattern() != null) {
                    method = methodSelector(condition.methodPattern());
                } else if (condition.FIELD() != null && condition.NAME() != null) {
                    fieldName = condition.valueToken().getText();
                } else if (condition.FIELD() != null && condition.TYPE() != null) {
                    fieldType = condition.qualifiedName().getText();
                } else if (condition.PARAMETER() != null && condition.NAME() != null) {
                    parameterName = condition.valueToken().getText();
                } else if (condition.PARAMETER() != null && condition.TYPE() != null) {
                    parameterType = condition.qualifiedName().getText();
                } else if (condition.METHOD() != null && condition.NAME() != null) {
                    methodName = condition.valueToken().getText();
                } else if (condition.CALL() != null && condition.NAME() != null) {
                    callName = condition.valueToken().getText();
                } else if (condition.CALL() != null && condition.OWNER() != null) {
                    callOwner = condition.qualifiedName().getText();
                } else if (condition.ASSIGNMENT() != null) {
                    assignmentField = condition.valueToken().getText();
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

        private FindSpec buildFind(SerParser.FindDeclContext ctx) {
            if (ctx.annotationRef() != null) {
                JavaElementKind target;
                if (ctx.CLASS() != null) {
                    target = JavaElementKind.CLASS;
                } else if (ctx.FIELD() != null) {
                    target = JavaElementKind.FIELD;
                } else {
                    target = JavaElementKind.METHOD;
                }
                return new FindSpec(
                        target,
                        null,
                        annotation(target, ctx.annotationRef()),
                        null);
            }
            if (ctx.FIELD() != null) {
                return new FindSpec(
                        JavaElementKind.FIELD,
                        ctx.fieldName != null ? ctx.fieldName.getText() : null,
                        null,
                        null);
            }
            if (ctx.CLASS() != null) {
                return new FindSpec(JavaElementKind.CLASS, null, null, null);
            }
            if (ctx.methodPattern() != null) {
                return new FindSpec(JavaElementKind.METHOD, null, null, methodSelector(ctx.methodPattern()));
            }
            return new FindSpec(
                    null,
                    ctx.genericFindKind.getText(),
                    ctx.genericFindName != null ? ctx.genericFindName.getText() : null,
                    null,
                    null);
        }

        private LetSpec buildLet(SerParser.LetDeclContext ctx) {
            String name = ctx.letName.getText();
            List<SourceSpec> sources = ctx.sourceLine().stream().map(this::buildSource).toList();
            String defaultValue = ctx.defaultLine() != null ? literal(ctx.defaultLine().literal()) : null;
            Map<String, String> mapping = ctx.mapBlock() != null ? map(ctx.mapBlock()) : Map.of();
            return new LetSpec(name, sources, defaultValue, mapping);
        }

        private SourceSpec buildSource(SerParser.SourceLineContext ctx) {
            SerParser.SourceExprContext src = ctx.sourceExpr();
            TakeSpec take = take(ctx.takeExpr());

            if (src.annotationRef() != null) {
                JavaElementKind on = element(src.elementRef());
                return new SourceSpec(
                        JavaElementKind.ANNOTATION,
                        on,
                        null,
                        null,
                        annotation(on, src.annotationRef()),
                        null,
                        null,
                        take);
            }
            if (src.ARGUMENT() != null) {
                return new SourceSpec(
                        JavaElementKind.ARGUMENT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Integer.parseInt(src.INT().getText()),
                        take);
            }
            if (src.CALL() != null) {
                return new SourceSpec(JavaElementKind.CALL, null, null, null, null, null, null, take);
            }
            if (src.METHOD() != null) {
                return new SourceSpec(JavaElementKind.METHOD, null, null, null, null, null, null, take);
            }
            if (src.CLASS() != null) {
                return new SourceSpec(JavaElementKind.CLASS, null, null, null, null, null, null, take);
            }
            if (src.FIELD() != null) {
                return new SourceSpec(
                        JavaElementKind.FIELD,
                        null,
                        src.sourceName != null ? src.sourceName.getText() : null,
                        null,
                        null,
                        null,
                        null,
                        take);
            }
            if (src.PARAMETER() != null) {
                return new SourceSpec(
                        JavaElementKind.PARAMETER,
                        null,
                        src.sourceName != null ? src.sourceName.getText() : null,
                        null,
                        null,
                        null,
                        null,
                        take);
            }
            if (src.RETURN() != null) {
                return new SourceSpec(JavaElementKind.RETURN, null, null, null, null, null, null, take);
            }
            if (src.ASSIGNMENT() != null) {
                return new SourceSpec(JavaElementKind.ASSIGNMENT, null, null, null, null, null, null, take);
            }
            if (src.NEW() != null) {
                return new SourceSpec(
                        JavaElementKind.NEW,
                        null,
                        src.qualifiedName().getText(),
                        null,
                        null,
                        null,
                        null,
                        take);
            }
            if (src.LITERAL() != null) {
                return new SourceSpec(
                        JavaElementKind.LITERAL,
                        null,
                        null,
                        literal(src.literal()),
                        null,
                        null,
                        null,
                        take);
            }
            return new SourceSpec(
                    null,
                    src.genericSourceKind.getText(),
                    null,
                    null,
                    src.genericSourceName != null ? src.genericSourceName.getText() : null,
                    null,
                    null,
                    null,
                    null,
                    take);
        }

        private BuildSpec buildBuild(SerParser.BuildDeclContext ctx) {
            Map<String, BuildExpression> fields = new LinkedHashMap<>();
            for (SerParser.BuildFieldContext field : ctx.buildField()) {
                fields.put(field.buildFieldName().getText(), buildExpression(field));
            }
            return new BuildSpec(fields);
        }

        private BuildExpression buildExpression(SerParser.BuildFieldContext field) {
            SerParser.BuildExprContext expr = field.buildExpr();
            String reference = null;
            String constValue = null;
            List<String> concat = null;

            if (expr.CONCAT() != null) {
                concat = concatList(expr.concatList());
            } else if (expr.refName != null) {
                reference = expr.refName.getText();
            } else {
                constValue = unquote(expr.STRING().getText());
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
                        BuildActionKind.NORMALIZE,
                        null,
                        null,
                        null,
                        normalize(step.IDENT().getText()),
                        null);
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
            return new BuildAction(BuildActionKind.MAP, null, null, null, null, mapEntries(step.mapEntry()));
        }

        private TakeSpec take(SerParser.TakeExprContext ctx) {
            if (ctx.ATTR() != null) {
                return new TakeSpec(TakeKind.ATTRIBUTE, identList(ctx.identList()));
            }
            if (ctx.NAME() != null) {
                return new TakeSpec(TakeKind.NAME, List.of());
            }
            if (ctx.VALUE() != null) {
                return new TakeSpec(TakeKind.VALUE, List.of());
            }
            if (ctx.RAW() != null) {
                return new TakeSpec(TakeKind.RAW, List.of());
            }
            if (ctx.OWNER() != null) {
                return new TakeSpec(TakeKind.OWNER, List.of());
            }
            if (ctx.SIGNATURE() != null) {
                return new TakeSpec(TakeKind.SIGNATURE, List.of());
            }
            if (ctx.TYPE() != null) {
                return new TakeSpec(TakeKind.TYPE, List.of());
            }
            return new TakeSpec(null, ctx.genericTake.getText(), List.of());
        }

        private MethodSelector methodSelector(SerParser.MethodPatternContext ctx) {
            String owner = ctx.qualifiedName().getText();
            List<String> names =
                    ctx.identList() != null ? identList(ctx.identList()) : List.of(ctx.IDENT().getText());
            return new MethodSelector(owner, null, names, null);
        }

        private AnnotationSelector annotation(JavaElementKind on, SerParser.AnnotationRefContext ctx) {
            String text = ctx.getText().substring(1);
            if (text.startsWith("*")) {
                return new AnnotationSelector(on, List.of(), ".*" + text.substring(1));
            }
            return new AnnotationSelector(on, List.of(text), null);
        }

        private Map<String, String> map(SerParser.MapBlockContext ctx) {
            return mapEntries(ctx.mapEntry());
        }

        private Map<String, String> mapEntries(List<SerParser.MapEntryContext> entries) {
            Map<String, String> out = new LinkedHashMap<>();
            for (SerParser.MapEntryContext entry : entries) {
                out.put(entry.valueToken(0).getText(), entry.valueToken(1).getText());
            }
            return out;
        }

        private List<String> identList(SerParser.IdentListContext ctx) {
            return ctx.nameItem().stream().map(i -> i.getText()).toList();
        }

        private List<String> concatList(SerParser.ConcatListContext ctx) {
            return ctx.concatItem().stream()
                    .map(item -> item.STRING() != null ? unquote(item.STRING().getText()) : item.nameItem().getText())
                    .toList();
        }

        private JavaElementKind element(SerParser.ElementRefContext ctx) {
            return switch (ctx.getText()) {
                case "class" -> JavaElementKind.CLASS;
                case "method" -> JavaElementKind.METHOD;
                case "field" -> JavaElementKind.FIELD;
                case "parameter" -> JavaElementKind.PARAMETER;
                default -> throw new IllegalArgumentException("Unsupported Java element: " + ctx.getText());
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

        private String literal(SerParser.LiteralContext ctx) {
            if (ctx.STRING() != null) {
                return unquote(ctx.STRING().getText());
            }
            return ctx.valueToken().getText();
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
                    "Invalid SER syntax at line "
                            + line
                            + ", column "
                            + charPositionInLine
                            + ": "
                            + msg,
                    e);
        }
    }
}
