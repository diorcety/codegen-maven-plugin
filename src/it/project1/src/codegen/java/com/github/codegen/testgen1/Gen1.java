/**
 * com.github.codegen.testgen1.Gen1.java
 *
 * Copyright (c) 2007-2014 UShareSoft SAS, All rights reserved
 * @author UShareSoft
 */
package com.github.codegen.testgen1;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;

import java.util.Set;
import java.util.TreeSet;

public class Gen1 extends AbstractProcessor<CtClass> {

    @Override
    public void process(CtClass ctClass) {
        CtClass<Object> objectCtClass = getFactory().Class().create(ctClass.getQualifiedName() + "Builder");
        for (CtMethod<?> method : (Set<CtMethod<?>>)ctClass.getMethods()) {
            String simpleName = method.getSimpleName();
            if(simpleName.startsWith("set")) {
                if(method.getType().getActualClass().equals(void.class)) {
                    Set<ModifierKind> x = new TreeSet<ModifierKind>();
                    x.add(ModifierKind.PUBLIC);
                    CtMethod<Object> objectCtMethod = getFactory().Method().create(objectCtClass, x, objectCtClass.getReference(), simpleName, method.getParameters(), method.getThrownTypes());
                    CtReturn<Object> aReturn = getFactory().Core().createReturn();
                    aReturn.setReturnedExpression(getFactory().Core().createThisAccess());
                    CtBlock body = getFactory().Core().createBlock();
                    body.addStatement(aReturn);
                    objectCtMethod.setBody(body);
                }
            }
        }
    }

    @Override
    public boolean isToBeProcessed(CtClass candidate) {
        return candidate.getQualifiedName().startsWith("com.github.codegen.test1.");
    }

    @Override
    public void processingDone() {
        super.processingDone();
        System.out.print("Done");
    }
}
