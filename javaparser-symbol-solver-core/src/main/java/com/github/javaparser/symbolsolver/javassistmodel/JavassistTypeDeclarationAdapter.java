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

import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import javassist.*;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;

import java.util.*;


/**
 * @author Federico Tomassetti
 */
public class JavassistTypeDeclarationAdapter {

  private CtClass ctClass;
  private TypeSolver typeSolver;

  public JavassistTypeDeclarationAdapter(CtClass ctClass, TypeSolver typeSolver) {
    this.ctClass = ctClass;
    this.typeSolver = typeSolver;
  }

  public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
      Set<ResolvedMethodDeclaration> set = new HashSet<>();
      for (CtMethod m : ctClass.getDeclaredMethods()) {
          if (((m.getMethodInfo().getAccessFlags() & AccessFlag.BRIDGE) == 0)
                  && ((m.getMethodInfo().getAccessFlags() & AccessFlag.SYNTHETIC) == 0)) {
              JavassistMethodDeclaration javassistMethodDeclaration = new JavassistMethodDeclaration(m, typeSolver);
              set.add(javassistMethodDeclaration);
          }
      }
      return set;
  }

  public List<ResolvedConstructorDeclaration> getConstructors() {
      List<ResolvedConstructorDeclaration> list = new ArrayList<>();
      for (CtConstructor m : ctClass.getConstructors()) {
          if ((m.getMethodInfo().getAccessFlags() & AccessFlag.SYNTHETIC) == 0) {
              JavassistConstructorDeclaration javassistConstructorDeclaration = new JavassistConstructorDeclaration(m, typeSolver);
              list.add(javassistConstructorDeclaration);
          }
      }
      return list;
  }

  public List<ResolvedFieldDeclaration> getDeclaredFields() {
    List<ResolvedFieldDeclaration> fieldDecls = new ArrayList<>();
    collectDeclaredFields(ctClass, fieldDecls);
    return fieldDecls;
  }

  private void collectDeclaredFields(CtClass ctClass, List<ResolvedFieldDeclaration> fieldDecls) {
    if (ctClass != null) {
        for (CtField f : ctClass.getDeclaredFields()) {
            fieldDecls.add(new JavassistFieldDeclaration(f, typeSolver));
        }
        try {
        collectDeclaredFields(ctClass.getSuperclass(), fieldDecls);
      } catch (NotFoundException e) {
        // We'll stop here
      }
    }
  }

  public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
    if (null == ctClass.getGenericSignature()) {
      return Collections.emptyList();
    } else {
      try {
        SignatureAttribute.ClassSignature classSignature =
            SignatureAttribute.toClassSignature(ctClass.getGenericSignature());
          List<ResolvedTypeParameterDeclaration> list = new ArrayList<>();
          for (SignatureAttribute.TypeParameter tp : classSignature.getParameters()) {
              JavassistTypeParameter javassistTypeParameter = new JavassistTypeParameter(tp, JavassistFactory.toTypeDeclaration(ctClass, typeSolver), typeSolver);
              list.add(javassistTypeParameter);
          }
          return list;
      } catch (BadBytecode badBytecode) {
        throw new RuntimeException(badBytecode);
      }
    }
  }

  public Optional<ResolvedReferenceTypeDeclaration> containerType() {
    try {
      return ctClass.getDeclaringClass() == null ?
          Optional.empty() :
          Optional.of(JavassistFactory.toTypeDeclaration(ctClass.getDeclaringClass(), typeSolver));
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
