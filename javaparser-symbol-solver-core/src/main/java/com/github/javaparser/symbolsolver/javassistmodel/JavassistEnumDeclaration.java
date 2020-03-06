/*
 * Copyright (C) 2015-2016 Federico Tomassetti
 * Copyright (C) 2017-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.symbolsolver.javassistmodel;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.core.resolution.MethodUsageResolutionCapability;
import com.github.javaparser.symbolsolver.logic.AbstractTypeDeclaration;
import com.github.javaparser.symbolsolver.logic.MethodResolutionCapability;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.symbolsolver.resolution.MethodResolutionLogic;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.SyntheticAttribute;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Federico Tomassetti
 */
public class JavassistEnumDeclaration extends AbstractTypeDeclaration
        implements ResolvedEnumDeclaration, MethodResolutionCapability, MethodUsageResolutionCapability {

    private CtClass ctClass;
    private TypeSolver typeSolver;
    private JavassistTypeDeclarationAdapter javassistTypeDeclarationAdapter;

    public JavassistEnumDeclaration(CtClass ctClass, TypeSolver typeSolver) {
        if (ctClass == null) {
            throw new IllegalArgumentException();
        }
        if (!ctClass.isEnum()) {
            throw new IllegalArgumentException("Trying to instantiate a JavassistEnumDeclaration with something which is not an enum: " + ctClass.toString());
        }
        this.ctClass = ctClass;
        this.typeSolver = typeSolver;
        this.javassistTypeDeclarationAdapter = new JavassistTypeDeclarationAdapter(ctClass, typeSolver);
    }

    @Override
    public AccessSpecifier accessSpecifier() {
        return JavassistFactory.modifiersToAccessLevel(ctClass.getModifiers());
    }

    @Override
    public String getPackageName() {
        return ctClass.getPackageName();
    }

    @Override
    public String getClassName() {
        String name = ctClass.getName().replace('$', '.');
        if (getPackageName() != null) {
            return name.substring(getPackageName().length() + 1);
        }
        return name;
    }

    @Override
    public String getQualifiedName() {
        return ctClass.getName().replace('$', '.');
    }

    @Override
    public List<ResolvedReferenceType> getAncestors(boolean acceptIncompleteList) {
        // Direct ancestors of an enum are java.lang.Enum and interfaces
        List<ResolvedReferenceType> ancestors = new ArrayList<>();

        String superClassName = ctClass.getClassFile().getSuperclass();

        if (superClassName != null) {
            try {
                ancestors.add(new ReferenceTypeImpl(typeSolver.solveType(superClassName), typeSolver));
            } catch (UnsolvedSymbolException e) {
                if (!acceptIncompleteList) {
                    // we only throw an exception if we require a complete list; otherwise, we attempt to continue gracefully
                    throw e;
                }
            }
        }

        for (String interfazeName : ctClass.getClassFile().getInterfaces()) {
            try {
                ancestors.add(new ReferenceTypeImpl(typeSolver.solveType(interfazeName), typeSolver));
            } catch (UnsolvedSymbolException e) {
                if (!acceptIncompleteList) {
                    // we only throw an exception if we require a complete list; otherwise, we attempt to continue gracefully
                    throw e;
                }
            }
        }

        return ancestors;
    }

    @Override
    public ResolvedFieldDeclaration getField(String name) {
        Optional<ResolvedFieldDeclaration> field = Optional.empty();
        for (ResolvedFieldDeclaration f : javassistTypeDeclarationAdapter.getDeclaredFields()) {
            if (f.getName().equals(name)) {
                field = Optional.of(f);
                break;
            }
        }

        return field.orElseThrow(() -> new RuntimeException("Field " + name + " does not exist in " + ctClass.getName() + "."));
    }

    @Override
    public boolean hasField(String name) {
        for (ResolvedFieldDeclaration f : javassistTypeDeclarationAdapter.getDeclaredFields()) {
            if (f.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ResolvedFieldDeclaration> getAllFields() {
        return javassistTypeDeclarationAdapter.getDeclaredFields();
    }

    @Override
    public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
        return javassistTypeDeclarationAdapter.getDeclaredMethods();
    }

    @Override
    public boolean isAssignableBy(ResolvedType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasDirectlyAnnotation(String canonicalName) {
        return ctClass.hasAnnotation(canonicalName);
    }

    @Override
    public String getName() {
        String[] nameElements = ctClass.getSimpleName().replace('$', '.').split("\\.");
        return nameElements[nameElements.length - 1];
    }

    @Override
    public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
        return javassistTypeDeclarationAdapter.getTypeParameters();
    }

    @Override
    public Optional<ResolvedReferenceTypeDeclaration> containerType() {
        return javassistTypeDeclarationAdapter.containerType();
    }

    @Override
    public SymbolReference<ResolvedMethodDeclaration> solveMethod(String name, List<ResolvedType> argumentsTypes, boolean staticOnly) {
        List<ResolvedMethodDeclaration> candidates = new ArrayList<>();
        Predicate<CtMethod> staticOnlyCheck = m -> !staticOnly || (staticOnly && Modifier.isStatic(m.getModifiers()));
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            boolean isSynthetic = method.getMethodInfo().getAttribute(SyntheticAttribute.tag) != null;
            boolean isNotBridge = (method.getMethodInfo().getAccessFlags() & AccessFlag.BRIDGE) == 0;
            if (method.getName().equals(name) && !isSynthetic && isNotBridge && staticOnlyCheck.test(method)) {
                candidates.add(new JavassistMethodDeclaration(method, typeSolver));
            }
        }

        try {
            CtClass superClass = ctClass.getSuperclass();
            if (superClass != null) {
                SymbolReference<ResolvedMethodDeclaration> ref = new JavassistClassDeclaration(superClass, typeSolver).solveMethod(name, argumentsTypes, staticOnly);
                if (ref.isSolved()) {
                    candidates.add(ref.getCorrespondingDeclaration());
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        return MethodResolutionLogic.findMostApplicable(candidates, name, argumentsTypes, typeSolver);
    }

    public Optional<MethodUsage> solveMethodAsUsage(String name, List<ResolvedType> argumentsTypes,
                                                    Context invokationContext, List<ResolvedType> typeParameterValues) {
        return JavassistUtils.getMethodUsage(ctClass, name, argumentsTypes, typeSolver, getTypeParameters(), typeParameterValues);
    }

    @Override
    public Set<ResolvedReferenceTypeDeclaration> internalTypes() {
        try {
            /*
            Get all internal types of the current class and get their corresponding ReferenceTypeDeclaration.
            Finally, return them in a Set.
             */
            Set<ResolvedReferenceTypeDeclaration> set = new HashSet<>();
            for (CtClass itype : ctClass.getDeclaredClasses()) {
                ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = JavassistFactory.toTypeDeclaration(itype, typeSolver);
                set.add(resolvedReferenceTypeDeclaration);
            }
            return set;
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResolvedReferenceTypeDeclaration getInternalType(String name) {
        /*
        The name of the ReferenceTypeDeclaration could be composed on the internal class and the outer class, e.g. A$B. That's why we search the internal type in the ending part.
        In case the name is composed of the internal type only, i.e. f.getName() returns B, it will also works.
         */
        Optional<ResolvedReferenceTypeDeclaration> type =
                Optional.empty();
        for (ResolvedReferenceTypeDeclaration f : this.internalTypes()) {
            if (f.getName().endsWith(name)) {
                type = Optional.of(f);
                break;
            }
        }
        return type.orElseThrow(() ->
                new UnsolvedSymbolException("Internal type not found: " + name));
    }

    @Override
    public boolean hasInternalType(String name) {
        /*
        The name of the ReferenceTypeDeclaration could be composed on the internal class and the outer class, e.g. A$B. That's why we search the internal type in the ending part.
        In case the name is composed of the internal type only, i.e. f.getName() returns B, it will also works.
         */
        for (ResolvedReferenceTypeDeclaration f : this.internalTypes()) {
            if (f.getName().endsWith(name)) {
                return true;
            }
        }
        return false;
    }

    public SymbolReference<? extends ResolvedValueDeclaration> solveSymbol(String name, TypeSolver typeSolver) {
        for (CtField field : ctClass.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return SymbolReference.solved(new JavassistFieldDeclaration(field, typeSolver));
            }
        }

        String[] interfaceFQNs = getInterfaceFQNs();
        for (String interfaceFQN : interfaceFQNs) {
            SymbolReference<? extends ResolvedValueDeclaration> interfaceRef = solveSymbolForFQN(name, interfaceFQN);
            if (interfaceRef.isSolved()) {
                return interfaceRef;
            }
        }

        return SymbolReference.unsolved(ResolvedValueDeclaration.class);
    }

    private SymbolReference<? extends ResolvedValueDeclaration> solveSymbolForFQN(String symbolName, String fqn) {
        if (fqn == null) {
            return SymbolReference.unsolved(ResolvedValueDeclaration.class);
        }

        ResolvedReferenceTypeDeclaration fqnTypeDeclaration = typeSolver.solveType(fqn);
        return new SymbolSolver(typeSolver).solveSymbolInType(fqnTypeDeclaration, symbolName);
    }

    private String[] getInterfaceFQNs() {
        return ctClass.getClassFile().getInterfaces();
    }

    @Override
    public List<ResolvedEnumConstantDeclaration> getEnumConstants() {
        List<ResolvedEnumConstantDeclaration> list = new ArrayList<>();
        for (CtField f : ctClass.getFields()) {
            if ((f.getFieldInfo2().getAccessFlags() & AccessFlag.ENUM) != 0) {
                JavassistEnumConstantDeclaration javassistEnumConstantDeclaration = new JavassistEnumConstantDeclaration(f, typeSolver);
                list.add(javassistEnumConstantDeclaration);
            }
        }
        return list;
    }

    @Override
    public List<ResolvedConstructorDeclaration> getConstructors() {
        return javassistTypeDeclarationAdapter.getConstructors();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "ctClass=" + ctClass.getName() +
                ", typeSolver=" + typeSolver +
                '}';
    }
}
