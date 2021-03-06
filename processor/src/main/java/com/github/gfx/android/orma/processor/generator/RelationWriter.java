/*
 * Copyright (c) 2015 FUJI Goro (gfx).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gfx.android.orma.processor.generator;

import com.github.gfx.android.orma.processor.ProcessingContext;
import com.github.gfx.android.orma.processor.model.SchemaDefinition;
import com.github.gfx.android.orma.processor.util.Annotations;
import com.github.gfx.android.orma.processor.util.Types;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

public class RelationWriter extends BaseWriter {

    private final SchemaDefinition schema;

    private final ConditionQueryHelpers queryHelpers;

    public RelationWriter(ProcessingContext context, SchemaDefinition schema) {
        super(context);
        this.schema = schema;
        this.queryHelpers = new ConditionQueryHelpers(context, schema, getTargetClassName());
    }

    ClassName getTargetClassName() {
        return schema.getRelationClassName();
    }

    @Override
    public String getPackageName() {
        return schema.getPackageName();
    }

    @Override
    public Optional<? extends Element> getElement() {
        return Optional.of(schema.getElement());
    }

    @Override
    public TypeSpec buildTypeSpec() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(getTargetClassName());
        if (schema.isGeneric()) {
            classBuilder.addAnnotation(Annotations.suppressWarnings("rawtypes"));
        }
        classBuilder.addModifiers(Modifier.PUBLIC);
        classBuilder.superclass(Types.getRelation(schema.getModelClassName(), getTargetClassName()));

        classBuilder.addField(FieldSpec.builder(schema.getSchemaClassName(), "schema", Modifier.FINAL).build());

        classBuilder.addMethods(buildMethodSpecs());

        return classBuilder.build();
    }

    public List<MethodSpec> buildMethodSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        methodSpecs.addAll(
                new ConditionBaseMethods(context, schema, getTargetClassName())
                        .buildMethodSpecs());

        if (schema.hasPrimaryIdEqHelper()) {
            schema.getPrimaryKey().ifPresent(primaryKey -> {
                methodSpecs.add(MethodSpec.methodBuilder("reload")
                        .addAnnotation(Annotations.nonNull())
                        .addAnnotation(Annotations.checkResult())
                        .addModifiers(Modifier.PUBLIC)
                        .returns(schema.getModelClassName())
                        .addParameter(ParameterSpec.builder(schema.getModelClassName(), "model")
                                .addAnnotation(Annotations.nonNull())
                                .build())
                        .addStatement("return selector().$LEq($L).value()",
                                primaryKey.name, primaryKey.buildGetColumnExpr("model"))
                        .build());
            });
        }

        methodSpecs.add(MethodSpec.methodBuilder("selector")
                .addAnnotations(Annotations.overrideAndNonNull())
                .addModifiers(Modifier.PUBLIC)
                .returns(schema.getSelectorClassName())
                .addStatement("return new $T(this)", schema.getSelectorClassName())
                .build());

        methodSpecs.add(MethodSpec.methodBuilder("updater")
                .addAnnotations(Annotations.overrideAndNonNull())
                .addModifiers(Modifier.PUBLIC)
                .returns(schema.getUpdaterClassName())
                .addStatement("return new $T(this)", schema.getUpdaterClassName())
                .build());

        methodSpecs.add(MethodSpec.methodBuilder("deleter")
                .addAnnotations(Annotations.overrideAndNonNull())
                .addModifiers(Modifier.PUBLIC)
                .returns(schema.getDeleterClassName())
                .addStatement("return new $T(this)", schema.getDeleterClassName())
                .build());

        methodSpecs.addAll(queryHelpers.buildConditionHelpers(true));

        return methodSpecs;
    }
}
