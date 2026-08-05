package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.List;

/**
 * JDT execution boundary for the static extract rule model.
 */
public interface JdtStaticExtractEngine {

    List<StaticExtractResult> execute(
            StaticExtractRule rule,
            CompilationUnit compilationUnit,
            TypeDeclaration typeDeclaration,
            String projectFilePath,
            String absoluteFilePath);
}
