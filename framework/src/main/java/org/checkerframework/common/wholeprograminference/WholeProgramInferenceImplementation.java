package org.checkerframework.common.wholeprograminference;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.dataflow.analysis.Analysis;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.expression.ClassName;
import org.checkerframework.dataflow.expression.FieldAccess;
import org.checkerframework.dataflow.expression.ThisReference;
import org.checkerframework.framework.flow.CFAbstractStore;
import org.checkerframework.framework.flow.CFAbstractValue;
import org.checkerframework.framework.qual.IgnoreInWholeProgramInference;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedNullType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.framework.util.dependenttypes.DependentTypesHelper;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 * WholeProgramInferenceImplementation is an implementation of {@link
 * org.checkerframework.common.wholeprograminference.WholeProgramInference}.
 *
 * <p>Its file format is ajava files.
 *
 * <p>It stores annotations directly with the JavaParser nodes they apply to.
 *
 * <p>See {@link org.checkerframework.common.wholeprograminference.WholeProgramInferenceScenes} for
 * more documentation on behavior.
 */
public class WholeProgramInferenceImplementation<T> implements WholeProgramInference {

    /** The type factory associated with this. */
    protected final AnnotatedTypeFactory atypeFactory;

    private WholeProgramInferenceStorage<T> storage;

    /** Indicates whether assignments where the rhs is null should be ignored. */
    private final boolean ignoreNullAssignments;

    /**
     * Constructs a new {@code WholeProgramInferenceImplementation} that has not yet inferred any
     * annotations.
     *
     * @param atypeFactory the associated type factory
     */
    public WholeProgramInferenceImplementation(
            AnnotatedTypeFactory atypeFactory, WholeProgramInferenceStorage<T> storage) {
        this.atypeFactory = atypeFactory;
        this.storage = storage;
        boolean isNullness =
                atypeFactory.getClass().getSimpleName().equals("NullnessAnnotatedTypeFactory");
        this.ignoreNullAssignments = !isNullness;
    }

    @Override
    public void updateFromObjectCreation(
            ObjectCreationNode objectCreationNode,
            ExecutableElement constructorElt,
            CFAbstractStore<?, ?> store) {
        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(constructorElt)) {
            return;
        }

        String file = storage.getFileForElement(constructorElt);
        List<Node> arguments = objectCreationNode.getArguments();
        updateInferredExecutableParameterTypes(constructorElt, file, arguments);
        updateContracts(Analysis.BeforeOrAfter.BEFORE, constructorElt, store);
    }

    @Override
    public void updateFromMethodInvocation(
            MethodInvocationNode methodInvNode,
            Tree receiverTree,
            ExecutableElement methodElt,
            CFAbstractStore<?, ?> store) {
        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(methodElt)) {
            return;
        }

        String file = storage.getFileForElement(methodElt);
        if (!storage.hasMethodAnnos(methodElt)) {
            return;
        }

        List<Node> arguments = methodInvNode.getArguments();
        updateInferredExecutableParameterTypes(methodElt, file, arguments);
        updateContracts(Analysis.BeforeOrAfter.BEFORE, methodElt, store);
    }

    /**
     * Updates inferred parameter types based on a call to a method or constructor.
     *
     * @param methodElt the element of the method or constructor being invoked
     * @param file the annotation file containing the executable; used for marking the class as
     *     modified (needing to be written to disk)
     * @param arguments the arguments of the invocation
     */
    private void updateInferredExecutableParameterTypes(
            ExecutableElement methodElt, String file, List<Node> arguments) {

        for (int i = 0; i < arguments.size(); i++) {
            Node arg = arguments.get(i);
            Tree argTree = arg.getTree();
            if (argTree == null) {
                // TODO: Handle variable-length list as parameter.
                // An ArrayCreationNode with a null tree is created when the
                // parameter is a variable-length list. We are ignoring it for now.
                // See Issue 682
                // https://github.com/typetools/checker-framework/issues/682
                continue;
            }

            VariableElement ve = methodElt.getParameters().get(i);
            AnnotatedTypeMirror paramATM = atypeFactory.getAnnotatedType(ve);
            AnnotatedTypeMirror argATM = atypeFactory.getAnnotatedType(argTree);
            atypeFactory.wpiAdjustForUpdateNonField(argATM);
            T paramType = storage.getParameterType(methodElt, file, i, paramATM, ve, atypeFactory);
            updateAnnotationSet(paramType, TypeUseLocation.PARAMETER, argATM, paramATM, file);
        }
    }

    @Override
    public void updateContracts(
            Analysis.BeforeOrAfter preOrPost,
            ExecutableElement methodElt,
            CFAbstractStore<?, ?> store) {
        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(methodElt)) {
            return;
        }

        if (store == null) {
            throw new BugInCF(
                    "updateContracts(%s, %s, null) for %s",
                    preOrPost, methodElt, atypeFactory.getClass().getSimpleName());
        }

        String file = storage.getFileForElement(methodElt);
        if (!storage.hasMethodAnnos(methodElt)) {
            return;
        }

        // TODO: Probably move some part of this into the AnnotatedTypeFactory.

        // This code only handles fields of "this", for now.  In the future, extend it to other
        // expressions.
        TypeElement containingClass = (TypeElement) methodElt.getEnclosingElement();
        ThisReference thisReference = new ThisReference(containingClass.asType());
        ClassName classNameReceiver = new ClassName(containingClass.asType());
        for (VariableElement fieldElement :
                ElementFilter.fieldsIn(containingClass.getEnclosedElements())) {
            FieldAccess fa =
                    new FieldAccess(
                            (ElementUtils.isStatic(fieldElement)
                                    ? classNameReceiver
                                    : thisReference),
                            fieldElement.asType(),
                            fieldElement);
            CFAbstractValue<?> v = store.getFieldValue(fa);
            AnnotatedTypeMirror fieldDeclType = atypeFactory.getAnnotatedType(fieldElement);
            AnnotatedTypeMirror inferredType;
            if (v != null) {
                // This field is in the store.
                inferredType = convertCFAbstractValueToAnnotatedTypeMirror(v, fieldDeclType);
                atypeFactory.wpiAdjustForUpdateNonField(inferredType);
            } else {
                // This field is not in the store. Add its declared type.
                inferredType = atypeFactory.getAnnotatedType(fieldElement);
            }
            T preOrPostConditionAnnos =
                    storage.getMethodContractForField(methodElt, file, preOrPost, fieldElement);
            updateAnnotationSet(
                    preOrPostConditionAnnos,
                    TypeUseLocation.FIELD,
                    inferredType,
                    fieldDeclType,
                    file,
                    false);
        }
    }

    /**
     * Converts a CFAbstractValue to an AnnotatedTypeMirror.
     *
     * @param v a value to convert to an AnnotatedTypeMirror
     * @param fieldType a type that is copied, then updated to use {@code v}'s annotations
     * @return a copy of {@code fieldType} with {@code v}'s annotations
     */
    private AnnotatedTypeMirror convertCFAbstractValueToAnnotatedTypeMirror(
            CFAbstractValue<?> v, AnnotatedTypeMirror fieldType) {
        AnnotatedTypeMirror result = fieldType.deepCopy();
        result.replaceAnnotations(v.getAnnotations());
        return result;
    }

    @Override
    public void updateFromOverride(
            MethodTree methodTree,
            ExecutableElement methodElt,
            AnnotatedExecutableType overriddenMethod) {
        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(methodElt)) {
            return;
        }

        String file = storage.getFileForElement(methodElt);

        for (int i = 0; i < overriddenMethod.getParameterTypes().size(); i++) {
            VariableElement ve = methodElt.getParameters().get(i);
            AnnotatedTypeMirror paramATM = atypeFactory.getAnnotatedType(ve);
            AnnotatedTypeMirror argATM = overriddenMethod.getParameterTypes().get(i);
            atypeFactory.wpiAdjustForUpdateNonField(argATM);
            T paramType = storage.getParameterType(methodElt, file, i, paramATM, ve, atypeFactory);
            updateAnnotationSet(paramType, TypeUseLocation.PARAMETER, argATM, paramATM, file);
        }

        AnnotatedDeclaredType argADT = overriddenMethod.getReceiverType();
        if (argADT != null) {
            AnnotatedTypeMirror paramATM =
                    atypeFactory.getAnnotatedType(methodTree).getReceiverType();
            if (paramATM != null) {
                T receiver = storage.getReceiverType(methodElt, file, paramATM, atypeFactory);
                updateAnnotationSet(receiver, TypeUseLocation.RECEIVER, argADT, paramATM, file);
            }
        }
    }

    @Override
    public void updateFromFormalParameterAssignment(
            LocalVariableNode lhs, Node rhs, VariableElement paramElt) {
        // Don't infer types for code that isn't presented as source.
        if (!isElementFromSourceCode(lhs)) {
            return;
        }

        Tree rhsTree = rhs.getTree();
        if (rhsTree == null) {
            // TODO: Handle variable-length list as parameter.
            // An ArrayCreationNode with a null tree is created when the
            // parameter is a variable-length list. We are ignoring it for now.
            // See Issue 682
            // https://github.com/typetools/checker-framework/issues/682
            return;
        }

        ExecutableElement methodElt = (ExecutableElement) paramElt.getEnclosingElement();
        String file = storage.getFileForElement(methodElt);
        AnnotatedTypeMirror paramATM = atypeFactory.getAnnotatedType(paramElt);
        AnnotatedTypeMirror argATM = atypeFactory.getAnnotatedType(rhsTree);
        atypeFactory.wpiAdjustForUpdateNonField(argATM);
        int i = methodElt.getParameters().indexOf(paramElt);
        assert i != -1;
        T paramType =
                storage.getParameterType(methodElt, file, i, paramATM, paramElt, atypeFactory);
        updateAnnotationSet(paramType, TypeUseLocation.PARAMETER, argATM, paramATM, file);
    }

    @Override
    public void updateFromFieldAssignment(Node lhs, Node rhs, ClassTree classTree) {

        Element element;
        String fieldName;
        if (lhs instanceof FieldAccessNode) {
            element = ((FieldAccessNode) lhs).getElement();
            fieldName = ((FieldAccessNode) lhs).getFieldName();
        } else if (lhs instanceof LocalVariableNode) {
            element = ((LocalVariableNode) lhs).getElement();
            fieldName = ((LocalVariableNode) lhs).getName();
        } else {
            throw new BugInCF(
                    "updateFromFieldAssignment received an unexpected node type: "
                            + lhs.getClass());
        }

        // TODO: For a primitive such as long, this is yielding just @GuardedBy rather than
        // @GuardedBy({}).
        AnnotatedTypeMirror rhsATM = atypeFactory.getAnnotatedType(rhs.getTree());
        atypeFactory.wpiAdjustForUpdateField(lhs.getTree(), element, fieldName, rhsATM);

        updateFieldFromType(lhs.getTree(), element, fieldName, rhsATM);
    }

    @Override
    public void updateFieldFromType(
            Tree lhsTree, Element element, String fieldName, AnnotatedTypeMirror rhsATM) {

        if (ignoreFieldInWPI(element, fieldName)) {
            return;
        }

        ClassSymbol enclosingClass = ((VarSymbol) element).enclClass();

        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(enclosingClass)) {
            return;
        }

        String file = storage.getFileForElement(element);

        @SuppressWarnings("signature") // https://tinyurl.com/cfissue/3094
        @BinaryName String className = enclosingClass.flatname.toString();
        AnnotatedTypeMirror lhsATM = atypeFactory.getAnnotatedType(lhsTree);
        T fieldType =
                storage.getFieldType(
                        className, file, enclosingClass, fieldName, lhsATM, atypeFactory);
        updateAnnotationSet(fieldType, TypeUseLocation.FIELD, rhsATM, lhsATM, file);
    }

    /**
     * Returns true if an assignment to the given field should be ignored by WPI.
     *
     * @param element the field's element
     * @param fieldName the field's name
     * @return true if an assignment to the given field should be ignored by WPI
     */
    protected boolean ignoreFieldInWPI(Element element, String fieldName) {
        // Do not attempt to infer types for fields that do not have valid
        // names. For example, compiler-generated temporary variables will
        // have invalid names. Recording facts about fields with
        // invalid names causes jaif-based WPI to crash when reading the .jaif
        // file, and stub-based WPI to generate unparseable stub files.
        // See https://github.com/typetools/checker-framework/issues/3442
        if (!SourceVersion.isIdentifier(fieldName)) {
            return true;
        }

        // If the inferred field has a declaration annotation with the
        // @IgnoreInWholeProgramInference meta-annotation, exit this routine.
        if (atypeFactory.getDeclAnnotation(element, IgnoreInWholeProgramInference.class) != null
                || atypeFactory
                                .getDeclAnnotationWithMetaAnnotation(
                                        element, IgnoreInWholeProgramInference.class)
                                .size()
                        > 0) {
            return true;
        }

        ClassSymbol enclosingClass = ((VarSymbol) element).enclClass();

        // Don't infer types for code that isn't presented as source.
        if (!ElementUtils.isElementFromSourceCode(enclosingClass)) {
            return true;
        }

        return false;
    }

    @Override
    public void updateFromReturn(
            ReturnNode retNode,
            ClassSymbol classSymbol,
            MethodTree methodTree,
            Map<AnnotatedDeclaredType, ExecutableElement> overriddenMethods) {
        // Don't infer types for code that isn't presented as source.
        if (methodTree == null
                || !ElementUtils.isElementFromSourceCode(
                        TreeUtils.elementFromDeclaration(methodTree))) {
            return;
        }

        // Whole-program inference ignores some locations.  See Issue 682:
        // https://github.com/typetools/checker-framework/issues/682
        if (classSymbol == null) { // TODO: Handle anonymous classes.
            return;
        }

        ExecutableElement methodElt = TreeUtils.elementFromDeclaration(methodTree);
        String file = storage.getFileForElement(methodElt);

        AnnotatedTypeMirror lhsATM = atypeFactory.getAnnotatedType(methodTree).getReturnType();

        // Type of the expression returned
        AnnotatedTypeMirror rhsATM =
                atypeFactory.getAnnotatedType(retNode.getTree().getExpression());
        atypeFactory.wpiAdjustForUpdateNonField(rhsATM);
        DependentTypesHelper dependentTypesHelper =
                ((GenericAnnotatedTypeFactory) atypeFactory).getDependentTypesHelper();
        if (dependentTypesHelper != null) {
            dependentTypesHelper.standardizeReturnType(
                    methodTree, rhsATM, /*removeErroneousExpressions=*/ true);
        }
        T returnTypeAnnos = storage.getReturnType(methodElt, file, lhsATM, atypeFactory);
        updateAnnotationSet(returnTypeAnnos, TypeUseLocation.RETURN, rhsATM, lhsATM, file);

        // Now, update return types of overridden methods based on the implementation we just saw.
        // This inference is similar to the inference procedure for method parameters: both are
        // updated based only on the implementations (in this case) or call-sites (for method
        // parameters) that are available to WPI.
        //
        // An alternative implementation would be to:
        //  * update only the method (not overridden methods)
        //  * when finished, propagate the final result to overridden methods
        //
        for (Map.Entry<AnnotatedDeclaredType, ExecutableElement> pair :
                overriddenMethods.entrySet()) {

            AnnotatedDeclaredType superclassDecl = pair.getKey();
            ExecutableElement overriddenMethodElement = pair.getValue();

            // Don't infer types for code that isn't presented as source.
            if (!ElementUtils.isElementFromSourceCode(overriddenMethodElement)) {
                continue;
            }

            AnnotatedExecutableType overriddenMethod =
                    AnnotatedTypes.asMemberOf(
                            atypeFactory.getProcessingEnv().getTypeUtils(),
                            atypeFactory,
                            superclassDecl,
                            overriddenMethodElement);

            String superClassFile = storage.getFileForElement(overriddenMethodElement);
            AnnotatedTypeMirror overriddenMethodReturnType = overriddenMethod.getReturnType();
            T storedOverriddenMethodReturnType =
                    storage.getReturnType(
                            overriddenMethodElement,
                            file,
                            overriddenMethodReturnType,
                            atypeFactory);

            updateAnnotationSet(
                    storedOverriddenMethodReturnType,
                    TypeUseLocation.RETURN,
                    rhsATM,
                    overriddenMethodReturnType,
                    superClassFile);
        }
    }

    @Override
    public void addMethodDeclarationAnnotation(ExecutableElement methodElt, AnnotationMirror anno) {

        // Do not infer types for library code, only for type-checked source code.
        if (!ElementUtils.isElementFromSourceCode(methodElt)) {
            return;
        }

        String file = storage.getFileForElement(methodElt);
        boolean isNewAnnotation = storage.addMethodDeclarationAnnotation(methodElt, anno, null);
        if (isNewAnnotation) {
            storage.setFileModified(file);
        }
    }

    /**
     * Updates the set of annotations in a location in a program.
     *
     * <ul>
     *   <li>If there was no previous annotation for that location, then the updated set will be the
     *       annotations in rhsATM.
     *   <li>If there was a previous annotation, the updated set will be the LUB between the
     *       previous annotation and rhsATM.
     * </ul>
     *
     * <p>Subclasses can customize its behavior.
     *
     * @param typeToUpdate the type whose annotations are modified by this method
     * @param defLoc the location where the annotation will be added
     * @param rhsATM the RHS of the annotated type on the source code
     * @param lhsATM the LHS of the annotated type on the source code
     * @param file path to the annotation file containing the executable; used for marking the scene
     *     as modified (needing to be written to disk)
     */
    protected void updateAnnotationSet(
            T typeToUpdate,
            TypeUseLocation defLoc,
            AnnotatedTypeMirror rhsATM,
            AnnotatedTypeMirror lhsATM,
            String file) {
        updateAnnotationSet(typeToUpdate, defLoc, rhsATM, lhsATM, file, true);
    }

    /**
     * Updates the set of annotations in a location in a program.
     *
     * <ul>
     *   <li>If there was no previous annotation for that location, then the updated set will be the
     *       annotations in rhsATM.
     *   <li>If there was a previous annotation, the updated set will be the LUB between the
     *       previous annotation and rhsATM.
     * </ul>
     *
     * <p>Subclasses can customize its behavior.
     *
     * @param typeToUpdate the type whose annotations are modified by this method
     * @param defLoc the location where the annotation will be added
     * @param rhsATM the RHS of the annotated type on the source code
     * @param lhsATM the LHS of the annotated type on the source code
     * @param file path to the annotation file containing the executable; used for marking the scene
     *     as modified (needing to be written to disk)
     * @param ignoreIfAnnotated if true, don't update any type that is explicitly annotated in the
     *     source code
     */
    protected void updateAnnotationSet(
            T typeToUpdate,
            TypeUseLocation defLoc,
            AnnotatedTypeMirror rhsATM,
            AnnotatedTypeMirror lhsATM,
            String file,
            boolean ignoreIfAnnotated) {
        if (rhsATM instanceof AnnotatedNullType && ignoreNullAssignments) {
            return;
        }
        AnnotatedTypeMirror atmFromStorage =
                storage.atmFromAnnotationLocation(rhsATM.getUnderlyingType(), typeToUpdate);
        updateATMWithLUB(rhsATM, atmFromStorage);
        if (lhsATM instanceof AnnotatedTypeVariable) {
            Set<AnnotationMirror> upperAnnos =
                    ((AnnotatedTypeVariable) lhsATM).getUpperBound().getEffectiveAnnotations();
            // If the inferred type is a subtype of the upper bounds of the
            // current type on the source code, halt.
            if (upperAnnos.size() == rhsATM.getAnnotations().size()
                    && atypeFactory
                            .getQualifierHierarchy()
                            .isSubtype(rhsATM.getAnnotations(), upperAnnos)) {
                return;
            }
        }
        storage.updateStorageLocationFromAtm(
                rhsATM, lhsATM, typeToUpdate, defLoc, ignoreIfAnnotated);
        storage.setFileModified(file);
    }

    /**
     * Updates sourceCodeATM to contain the LUB between sourceCodeATM and ajavaATM, ignoring missing
     * AnnotationMirrors from ajavaATM -- it considers the LUB between an AnnotationMirror am and a
     * missing AnnotationMirror to be am. The results are stored in sourceCodeATM.
     *
     * @param sourceCodeATM the annotated type on the source code
     * @param ajavaATM the annotated type on the ajava file
     */
    private void updateATMWithLUB(AnnotatedTypeMirror sourceCodeATM, AnnotatedTypeMirror ajavaATM) {

        switch (sourceCodeATM.getKind()) {
            case TYPEVAR:
                updateATMWithLUB(
                        ((AnnotatedTypeVariable) sourceCodeATM).getLowerBound(),
                        ((AnnotatedTypeVariable) ajavaATM).getLowerBound());
                updateATMWithLUB(
                        ((AnnotatedTypeVariable) sourceCodeATM).getUpperBound(),
                        ((AnnotatedTypeVariable) ajavaATM).getUpperBound());
                break;
                //        case WILDCARD:
                // Because inferring type arguments is not supported, wildcards won't be encoutered
                //            updatesATMWithLUB(atf, ((AnnotatedWildcardType)
                // sourceCodeATM).getExtendsBound(),
                //                              ((AnnotatedWildcardType)
                // ajavaATM).getExtendsBound());
                //            updatesATMWithLUB(atf, ((AnnotatedWildcardType)
                // sourceCodeATM).getSuperBound(),
                //                              ((AnnotatedWildcardType) ajavaATM).getSuperBound());
                //            break;
            case ARRAY:
                updateATMWithLUB(
                        ((AnnotatedArrayType) sourceCodeATM).getComponentType(),
                        ((AnnotatedArrayType) ajavaATM).getComponentType());
                break;
                // case DECLARED:
                // inferring annotations on type arguments is not supported, so no need to recur on
                // generic types. If this was every implemented, this method would need VisitHistory
                // object to prevent infinite recursion on types such as T extends List<T>.
            default:
                // ATM only has primary annotations
                break;
        }

        // LUB primary annotations
        Set<AnnotationMirror> annosToReplace = new HashSet<>();
        for (AnnotationMirror amSource : sourceCodeATM.getAnnotations()) {
            AnnotationMirror amAjava = ajavaATM.getAnnotationInHierarchy(amSource);
            // amAjava only contains  annotations from the ajava file, so it might be missing
            // an annotation in the hierarchy
            if (amAjava != null) {
                amSource = atypeFactory.getQualifierHierarchy().leastUpperBound(amSource, amAjava);
            }
            annosToReplace.add(amSource);
        }
        sourceCodeATM.replaceAnnotations(annosToReplace);
    }

    /**
     * Checks whether a given local variable came from a source file or not.
     *
     * <p>By contrast, {@link ElementUtils#isElementFromByteCode(Element)} returns true if there is
     * a classfile for the given element, whether or not there is also a source file.
     *
     * @param localVariableNode the local variable declaration to check
     * @return true if a source file containing the variable is being compiled
     */
    private boolean isElementFromSourceCode(LocalVariableNode localVariableNode) {
        return ElementUtils.isElementFromSourceCode(localVariableNode.getElement());
    }

    @Override
    public void writeResultsToFile(OutputFormat outputFormat, BaseTypeChecker checker) {
        storage.writeResultsToFile(outputFormat, checker);
    }

    @Override
    public void preprocessClassTree(ClassTree classTree) {
        storage.preprocessClassTree(classTree);
    }
}
