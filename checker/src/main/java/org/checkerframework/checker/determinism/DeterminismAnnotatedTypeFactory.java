package org.checkerframework.checker.determinism;

import com.sun.source.tree.*;
import java.lang.annotation.Annotation;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.determinism.qual.*;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.*;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.type.poly.QualifierPolymorphism;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.util.GraphQualifierHierarchy;
import org.checkerframework.framework.util.MultiGraphQualifierHierarchy;
import org.checkerframework.javacutil.*;

/** The annotated type factory for the determinism type-system. */
public class DeterminismAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {
    /** Annotation constants. */
    public final AnnotationMirror POLYDET, POLYDET_USE, POLYDET_UP, POLYDET_DOWN;

    public final AnnotationMirror NONDET = AnnotationBuilder.fromClass(elements, NonDet.class);
    public final AnnotationMirror ORDERNONDET =
            AnnotationBuilder.fromClass(elements, OrderNonDet.class);
    public final AnnotationMirror DET = AnnotationBuilder.fromClass(elements, Det.class);

    public DeterminismAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);

        POLYDET = newPolyDet("");
        POLYDET_USE = newPolyDet("use");
        POLYDET_UP = newPolyDet("up");
        POLYDET_DOWN = newPolyDet("down");

        postInit();
    }

    /** Creates an AnnotationMirror for {@code @PolyDet} with the given argument. */
    private AnnotationMirror newPolyDet(String arg) {
        AnnotationBuilder builder = new AnnotationBuilder(processingEnv, PolyDet.class);
        builder.setValue("value", arg);
        return builder.build();
    }

    @Override
    public QualifierPolymorphism createQualifierPolymorphism() {
        return new DeterminismQualifierPolymorphism(processingEnv, this);
    }

    @Override
    public CFTransfer createFlowTransferFunction(
            CFAbstractAnalysis<CFValue, CFStore, CFTransfer> analysis) {
        return new DeterminismTransfer((CFAnalysis) analysis);
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return new LinkedHashSet<>(
                Arrays.asList(Det.class, OrderNonDet.class, NonDet.class, PolyDet.class));
    }

    @Override
    public TreeAnnotator createTreeAnnotator() {
        return new ListTreeAnnotator(
                new DeterminismTreeAnnotator(this), super.createTreeAnnotator());
    }

    @Override
    protected TypeAnnotator createTypeAnnotator() {
        return new ListTypeAnnotator(
                super.createTypeAnnotator(),
                new DeterminismAnnotatedTypeFactory.DeterminismTypeAnnotator(this));
    }

    private class DeterminismTreeAnnotator extends TreeAnnotator {

        public DeterminismTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        // TODO: This documentation is incorrect.  It seems to do *defaulting*.  That means it
        // doesn't do any *replacement*, which is what the documentation says.  Make the code and
        // the documentation consistent with one another.
        // TODO: "equals() called on an {@code OrderNonDet Set}" doesn't make sense, because
        // equals() takes two arguments, not just one.
        // TODO: The description here seems simpler than the code, at least for the third case.  For
        // the documentation here, it seems @PolyDet("down") would work.  The actual code is more
        // complex, but it's confusing that it is inconsistent with this documentation.  You need to
        // always make them consistent.  In the rare case that the documentation is incomplete, say
        // that explicitly and point to where it is fully documented (which should not be just in
        // the code).
        /**
         * Replaces the annotation on the return type of a method invocation as follows:
         *
         * <ol>
         *   <li>The return type for static methods without any argument is {@code @Det}.
         *   <li>If {@code @PolyDet} resolves to {@code OrderNonDet} on a return type that isn't an
         *       array or a collection, it is replaced with {@code @NonDet}.
         *   <li>Return type of equals() called on an {@code OrderNonDet Set} gets the {@code @Det}
         *       annotation.
         * </ol>
         *
         * @param node Method invocation tree
         * @param p Annotated return type
         * @return Void
         */
        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, AnnotatedTypeMirror p) {
            AnnotatedTypeMirror receiver = atypeFactory.getReceiverType(node);

            // Receiver is null for abstract classes
            // (Example: Ordering.natural() in tests/all-systems/PolyCollectorTypeVars.java)
            if (receiver == null) {
                return super.visitMethodInvocation(node, p);
            }

            AnnotatedTypeMirror.AnnotatedExecutableType invokedMethod =
                    atypeFactory.methodFromUse(node).methodType;
            ExecutableElement invokedMethodElement = invokedMethod.getElement();

            // If return type (non-array and non-collection) resolves to @OrderNonDet, replace it
            // with @NonDet.
            if (p.getAnnotations().contains(ORDERNONDET)
                    && !(p.getUnderlyingType().getKind() == TypeKind.ARRAY)
                    && !(isCollection(TypesUtils.getTypeElement(p.getUnderlyingType()).asType()))
                    && !(isIterator(TypesUtils.getTypeElement(p.getUnderlyingType()).asType()))) {
                p.replaceAnnotation(NONDET);
            }

            // TODO: This is misleading.  The code seems to do defaulting, but the documentation
            // describes an absolute rule.
            // For static methods with no arguments, return type is annotated as @Det, not the
            // default @PolyDet.
            if (ElementUtils.isStatic(invokedMethodElement)) {
                if (node.getArguments().size() == 0) {
                    if (p.getExplicitAnnotations().size() == 0) {
                        p.replaceAnnotation(DET);
                    }
                }
            }

            // TODO: Use {@code ...} (note capitalization) only in Javadoc, not in Java code
            // comments.
            // For Sets: "equals" method should return @Det boolean
            // if the Set is @OrderNonDet and it does not have @OrderNonDet List type parameter.
            // Example {@Code @OrderNonDet Set<@OrderNonDet List<@Det Integer>> s1;
            //                @OrderNonDet Set<@OrderNonDet List<@Det Integer>> s2;
            //                s1.equals(s2) is @Det}
            // {@Code @OrderNonDet Set<@Det List<@Det Integer>> s1;
            //  @OrderNonDet Set<@Det List<@Det Integer>> s2;
            //  s1.equals(s2) is @NonDet}
            // TODO: this can be more precise (@Det receiver and @OrderNonDet parameter)
            TypeElement receiverUnderlyingType =
                    TypesUtils.getTypeElement(receiver.getUnderlyingType());

            // TODO:  I don't understand this.  Does some method in Collections crash?  Which one?
            // Or does this method crash when the argument is Collections?  Or something else?
            // Without this check, NullPointerException in Collections.
            // TODO: check why?
            if (receiverUnderlyingType == null) {
                return super.visitMethodInvocation(node, p);
            }

            if (isEqualsMethod(invokedMethodElement)
                    && isSet(receiverUnderlyingType.asType())
                    && AnnotationUtils.areSame(
                            receiver.getAnnotations().iterator().next(), ORDERNONDET)) {
                // Check that the receiver does not have "@OrderNonDet List" type parameter
                if (!hasOrderNonDetListAsTypeParameter(receiver)) {
                    AnnotatedTypeMirror parameter =
                            atypeFactory.getAnnotatedType(node.getArguments().get(0));
                    if (isSet(TypesUtils.getTypeElement(parameter.getUnderlyingType()).asType())
                            && parameter.hasAnnotation(ORDERNONDET)) {
                        // Check that the parameter does not have "@OrderNonDet List" type parameter
                        if (!hasOrderNonDetListAsTypeParameter(parameter)) {
                            p.replaceAnnotation(DET);
                        }
                    }
                }
            }
            return super.visitMethodInvocation(node, p);
        }

        /** Annotates the length property of a {@code @NonDet} array as {@code @NonDet}. */
        @Override
        public Void visitMemberSelect(
                MemberSelectTree node, AnnotatedTypeMirror annotatedTypeMirror) {
            if (TreeUtils.isArrayLengthAccess(node)) {
                AnnotatedTypeMirror.AnnotatedArrayType arrType =
                        (AnnotatedTypeMirror.AnnotatedArrayType)
                                atypeFactory.getAnnotatedType(node.getExpression());
                if (AnnotationUtils.areSame(arrType.getAnnotations().iterator().next(), NONDET)) {
                    annotatedTypeMirror.replaceAnnotation(NONDET);
                }
            }
            return super.visitMemberSelect(node, annotatedTypeMirror);
        }
    }

    /** Checks if {@code @OrderNonDet List} appears as a type parameter in {@code atm}. */
    private boolean hasOrderNonDetListAsTypeParameter(AnnotatedTypeMirror atm) {
        AnnotatedTypeMirror.AnnotatedDeclaredType declaredType =
                (AnnotatedTypeMirror.AnnotatedDeclaredType) atm;
        for (AnnotatedTypeMirror argType : declaredType.getTypeArguments()) {
            if (isList(argType.getUnderlyingType()) && argType.hasAnnotation(ORDERNONDET)) {
                return true;
            }
        }
        return false;
    }

    protected class DeterminismTypeAnnotator extends TypeAnnotator {
        public DeterminismTypeAnnotator(DeterminismAnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
        }

        // TODO: Is this defaulting?  The documentation doesn't say that, but indicates an absolute
        // rule.
        /**
         *
         *
         * <ol>
         *   <li>Annotates the main method parameters as {@code Det}.
         *   <li>Annotates array parameters and return types as {@code @PolyDet[@PolyDet]}.
         * </ol>
         */
        @Override
        public Void visitExecutable(
                final AnnotatedTypeMirror.AnnotatedExecutableType t, final Void p) {
            if (isMainMethod(t.getElement())) {
                AnnotatedTypeMirror paramType = t.getParameterTypes().get(0);
                paramType.replaceAnnotation(DET);
            } else {
                // Array return types should be annotated as @PolyDet[@PolyDet]
                AnnotatedTypeMirror retType = t.getReturnType();
                if (retType.getKind() == TypeKind.ARRAY) {
                    AnnotatedTypeMirror.AnnotatedArrayType arrRetType =
                            (AnnotatedTypeMirror.AnnotatedArrayType) retType;
                    if (arrRetType.getAnnotations().size() == 0
                            && arrRetType.getComponentType().getUnderlyingType().getKind()
                                    != TypeKind.TYPEVAR) {
                        arrRetType.replaceAnnotation(POLYDET);
                        arrRetType.getComponentType().replaceAnnotation(POLYDET);
                    }
                }

                // Array parameter types should be annotated as @PolyDet[@PolyDet]
                List<AnnotatedTypeMirror> paramTypes = t.getParameterTypes();
                for (AnnotatedTypeMirror paramType : paramTypes) {
                    if (paramType.getKind() == TypeKind.ARRAY
                            && paramType.getUnderlyingType().getKind() != TypeKind.TYPEVAR) {
                        AnnotatedTypeMirror.AnnotatedArrayType arrParamType =
                                (AnnotatedTypeMirror.AnnotatedArrayType) paramType;
                        if (arrParamType.getAnnotations().size() == 0) {
                            arrParamType.replaceAnnotation(POLYDET);
                            arrParamType.getComponentType().replaceAnnotation(POLYDET);
                        }
                    }
                }
            }
            return super.visitExecutable(t, p);
        }
    }

    /** @return true if {@code method} is equals */
    public static boolean isEqualsMethod(ExecutableElement method) {
        if (method.getReturnType().getKind() == TypeKind.BOOLEAN
                && method.getSimpleName().contentEquals("equals")
                && method.getParameters().size() == 1
                // TODO: duplicated line.
                && method.getParameters().size() == 1
                && TypesUtils.isObject(method.getParameters().get(0).asType())) {
            return true;
        }
        return false;
    }

    /** @return true if {@code method} is a main method */
    public static boolean isMainMethod(ExecutableElement method) {
        if (method.getReturnType().getKind() == TypeKind.VOID
                && method.getSimpleName().contentEquals("main")
                && method.getParameters().size() == 1
                && method.getParameters().get(0).asType().getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) method.getParameters().get(0).asType();
            if (TypesUtils.isString(arrayType.getComponentType())) {
                return true;
            }
        }
        return false;
    }

    // TODO: does "array parameters" mean all array parameters in any method?  Where is this
    // behavior described in the manual?
    /**
     * Adds implicit annotations for main method parameters ({@code @Det}) and array parameters
     * ({@code @PolyDet[@PolyDet]}).
     */
    @Override
    public void addComputedTypeAnnotations(Element elt, AnnotatedTypeMirror type) {
        if (elt.getKind() == ElementKind.PARAMETER) {
            if (elt.getEnclosingElement().getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) elt.getEnclosingElement();
                if (isMainMethod(method)) {
                    type.addMissingAnnotations(Collections.singleton(DET));
                } else if (type.getKind() == TypeKind.ARRAY && type.getAnnotations().size() == 0) {
                    AnnotatedTypeMirror.AnnotatedArrayType arrType =
                            (AnnotatedTypeMirror.AnnotatedArrayType) type;
                    if (arrType.getComponentType().getKind() != TypeKind.TYPEVAR) {
                        arrType.getComponentType()
                                .addMissingAnnotations(Collections.singleton(POLYDET));
                        type.addMissingAnnotations(Collections.singleton(POLYDET));
                    }
                }
            }
        }
        super.addComputedTypeAnnotations(elt, type);
    }

    // TODO: Why is this method public?  Give methods the smallest possible visibility.
    /** @return true if {@code tm} is a Set or a subtype of Set. */
    public boolean isSet(TypeMirror tm) {
        // TODO: Why compute this repeatedly?  I think you can store it in a field.
        TypeMirror SetInterfaceTypeMirror =
                TypesUtils.typeFromClass(Set.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(types.erasure(tm), types.erasure(SetInterfaceTypeMirror))) {
            return true;
        }
        return false;
    }

    /** @return true if {@code tm} is a List or a subtype of List. */
    public boolean isList(TypeMirror tm) {
        // List and subclasses
        TypeMirror ListInterfaceTypeMirror =
                TypesUtils.typeFromClass(List.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(types.erasure(tm), types.erasure(ListInterfaceTypeMirror))) {
            return true;
        }
        return false;
    }

    /** @return true if {@code tm} is a Collection or a subtype of Collection. */
    public boolean isCollection(TypeMirror tm) {
        javax.lang.model.util.Types types = processingEnv.getTypeUtils();
        TypeMirror CollectionInterfaceTypeMirror =
                TypesUtils.typeFromClass(Collection.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(types.erasure(tm), types.erasure(CollectionInterfaceTypeMirror))) {
            return true;
        }
        return false;
    }

    // TODO: or a subtype
    /** @return true if {@code tm} is an Iterator. */
    public boolean isIterator(TypeMirror tm) {
        javax.lang.model.util.Types types = processingEnv.getTypeUtils();
        TypeMirror IteratorTypeMirror =
                TypesUtils.typeFromClass(Iterator.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(tm, IteratorTypeMirror)) {
            return true;
        }
        return false;
    }

    // TODO: the Arrays class contains only static members.  It does not represent anything, and
    // therefore it doesn't make sense to override it.  I think you can just check for it exactly.
    /** @return true if {@code tm} is a subtype of the Arrays class. */
    public boolean isArrays(TypeMirror tm) {
        TypeMirror ArraysTypeMirror =
                TypesUtils.typeFromClass(Arrays.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(tm, ArraysTypeMirror)) {
            return true;
        }
        return false;
    }

    // TODO: Same comment as for isArrays
    /** @return true if {@code tm} is a subtype of the Collections class. */
    public boolean isCollections(TypeMirror tm) {
        TypeMirror CollectionsTypeMirror =
                TypesUtils.typeFromClass(Collections.class, types, processingEnv.getElementUtils());
        if (types.isSubtype(tm, CollectionsTypeMirror)) {
            return true;
        }
        return false;
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(
            MultiGraphQualifierHierarchy.MultiGraphFactory factory) {
        return new DeterminismQualifierHierarchy(factory, DET);
    }

    class DeterminismQualifierHierarchy extends GraphQualifierHierarchy {

        public DeterminismQualifierHierarchy(MultiGraphFactory f, AnnotationMirror bottom) {
            super(f, bottom);
        }

        /**
         * Treat {@code @PolyDet} with values as {@code @PolyDet} without values in the qualifier
         * hierarchy.
         */
        @Override
        public boolean isSubtype(AnnotationMirror subAnno, AnnotationMirror superAnno) {
            if (AnnotationUtils.areSameIgnoringValues(subAnno, POLYDET)) {
                subAnno = POLYDET;
            }
            if (AnnotationUtils.areSameIgnoringValues(superAnno, POLYDET)) {
                superAnno = POLYDET;
            }
            return super.isSubtype(subAnno, superAnno);
        }
    }
}
