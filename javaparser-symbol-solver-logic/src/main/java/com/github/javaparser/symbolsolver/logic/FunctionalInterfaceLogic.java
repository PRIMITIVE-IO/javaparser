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

package com.github.javaparser.symbolsolver.logic;

import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * @author Federico Tomassetti
 */
public final class FunctionalInterfaceLogic {

    private FunctionalInterfaceLogic() {
        // prevent instantiation
    }

    /**
     * Get the functional method defined by the type, if any.
     */
    public static Optional<MethodUsage> getFunctionalMethod(ResolvedType type) {
        if (type.isReferenceType() && type.asReferenceType().getTypeDeclaration().isInterface()) {
            return getFunctionalMethod(type.asReferenceType().getTypeDeclaration());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the functional method defined by the type, if any.
     */
    public static Optional<MethodUsage> getFunctionalMethod(ResolvedReferenceTypeDeclaration typeDeclaration) {
        //We need to find all abstract methods
        // Remove methods inherited by Object:
        // Consider the case of Comparator which define equals. It would be considered a functional method.
        Set<MethodUsage> methods = new HashSet<>();
        for (MethodUsage m : typeDeclaration.getAllMethods()) {
            if (m.getDeclaration().isAbstract()) {
                if (!declaredOnObject(m)) {
                    methods.add(m);
                }
            }
        }

        if (methods.size() == 1) {
            return Optional.of(methods.iterator().next());
        } else {
            return Optional.empty();
        }
    }

    public static boolean isFunctionalInterfaceType(ResolvedType type) {
        if (type.isReferenceType() && type.asReferenceType().getTypeDeclaration().hasAnnotation(FunctionalInterface.class.getCanonicalName())) {
            return true;
        }
        return getFunctionalMethod(type).isPresent();
    }

    private static String getSignature(Method m) {
        List<String> list = new ArrayList<>();
        for (Parameter p : m.getParameters()) {
            String s = toSignature(p);
            list.add(s);
        }
        return String.format("%s(%s)", m.getName(), String.join(", ", list));
    }

    private static String toSignature(Parameter p) {
        return p.getType().getCanonicalName();
    }

    private static List<String> OBJECT_METHODS_SIGNATURES;

    static {
        List<String> list = new ArrayList<>();
        for (Method method : Object.class.getDeclaredMethods()) {
            String signature = getSignature(method);
            list.add(signature);
        }
        OBJECT_METHODS_SIGNATURES = list;
    }

    private static boolean declaredOnObject(MethodUsage m) {
        return OBJECT_METHODS_SIGNATURES.contains(m.getDeclaration().getSignature());
    }
}
