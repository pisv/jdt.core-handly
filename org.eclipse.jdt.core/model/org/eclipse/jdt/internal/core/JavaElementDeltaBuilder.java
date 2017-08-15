/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Vladimir Piskarev <pisv@1c.ru> - Building large Java element deltas is really slow - https://bugs.eclipse.org/443928
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.handly.model.IElement;
import org.eclipse.handly.model.impl.ElementChangeRecorder;
import org.eclipse.handly.model.impl.ElementDelta;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * A java element delta builder creates a java element delta on
 * a java element between the version of the java element
 * at the time the comparator was created and the current version
 * of the java element.
 *
 * It performs this operation by locally caching the contents of
 * the java element when it is created. When the method
 * createDeltas() is called, it creates a delta over the cached
 * contents and the new contents.
 */
public class JavaElementDeltaBuilder {

	public JavaElementDelta delta;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ElementChangeRecorder recorder = new ElementChangeRecorder() {

		Map annotationInfos;

		@Override
		protected void recordBody(Object body, IElement element) {
			super.recordBody(body, element);
			IAnnotation[] annotations = null;
			if (body instanceof AnnotatableInfo)
				annotations = ((AnnotatableInfo) body).annotations;
			if (annotations != null) {
				if (this.annotationInfos == null)
					this.annotationInfos = new HashMap();
				JavaModelManager manager = JavaModelManager.getJavaModelManager();
				for (int i = 0, length = annotations.length; i < length; i++) {
					this.annotationInfos.put(annotations[i], manager.getInfo(annotations[i]));
				}
			}
		}

		@Override
		protected void findContentChange(Object oldBody, Object newBody, IElement element) {
			IJavaElement newElement = (IJavaElement) element;
			if (oldBody instanceof MemberElementInfo && newBody instanceof MemberElementInfo) {
				if (((MemberElementInfo)oldBody).getModifiers() != ((MemberElementInfo)newBody).getModifiers()) {
					JavaElementDeltaBuilder.this.delta.changed(newElement, IJavaElementDelta.F_MODIFIERS);
				}
				if (oldBody instanceof AnnotatableInfo && newBody instanceof AnnotatableInfo) {
					findAnnotationChanges(((AnnotatableInfo) oldBody).annotations, ((AnnotatableInfo) newBody).annotations, newElement);
				}
				if (oldBody instanceof SourceMethodElementInfo && newBody instanceof SourceMethodElementInfo) {
					SourceMethodElementInfo oldSourceMethodInfo = (SourceMethodElementInfo)oldBody;
					SourceMethodElementInfo newSourceMethodInfo = (SourceMethodElementInfo)newBody;
					if (!CharOperation.equals(oldSourceMethodInfo.getReturnTypeName(), newSourceMethodInfo.getReturnTypeName())
							|| !CharOperation.equals(oldSourceMethodInfo.getTypeParameterNames(), newSourceMethodInfo.getTypeParameterNames())
							|| !equals(oldSourceMethodInfo.getTypeParameterBounds(), newSourceMethodInfo.getTypeParameterBounds())) {
						JavaElementDeltaBuilder.this.delta.changed(newElement, IJavaElementDelta.F_CONTENT);
					}
				} else if (oldBody instanceof SourceFieldElementInfo && newBody instanceof SourceFieldElementInfo) {
					if (!CharOperation.equals(
							((SourceFieldElementInfo)oldBody).getTypeName(),
							((SourceFieldElementInfo)newBody).getTypeName())) {
						JavaElementDeltaBuilder.this.delta.changed(newElement, IJavaElementDelta.F_CONTENT);
					}
				} else if (oldBody instanceof SourceTypeElementInfo && newBody instanceof SourceTypeElementInfo) {
					SourceTypeElementInfo oldSourceTypeInfo = (SourceTypeElementInfo)oldBody;
					SourceTypeElementInfo newSourceTypeInfo = (SourceTypeElementInfo)newBody;
					if (!CharOperation.equals(oldSourceTypeInfo.getSuperclassName(), newSourceTypeInfo.getSuperclassName())
							|| !CharOperation.equals(oldSourceTypeInfo.getInterfaceNames(), newSourceTypeInfo.getInterfaceNames())) {
						JavaElementDeltaBuilder.this.delta.changed(newElement, IJavaElementDelta.F_SUPER_TYPES);
					}
					if (!CharOperation.equals(oldSourceTypeInfo.getTypeParameterNames(), newSourceTypeInfo.getTypeParameterNames())
							|| !equals(oldSourceTypeInfo.getTypeParameterBounds(), newSourceTypeInfo.getTypeParameterBounds())) {
						JavaElementDeltaBuilder.this.delta.changed(newElement, IJavaElementDelta.F_CONTENT);
					}
					HashMap oldTypeCategories = oldSourceTypeInfo.categories;
					HashMap newTypeCategories = newSourceTypeInfo.categories;
					if (oldTypeCategories != null) {
						// take the union of old and new categories elements (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=125675)
						Set elements;
						if (newTypeCategories != null) {
							elements = new HashSet(oldTypeCategories.keySet());
							elements.addAll(newTypeCategories.keySet());
						} else
							elements = oldTypeCategories.keySet();
						Iterator iterator = elements.iterator();
						while (iterator.hasNext()) {
							IJavaElement javaElement = (IJavaElement) iterator.next();
							String[] oldCategories = (String[]) oldTypeCategories.get(javaElement);
							String[] newCategories = newTypeCategories == null ? null : (String[]) newTypeCategories.get(javaElement);
							if (!Util.equalArraysOrNull(oldCategories, newCategories)) {
								JavaElementDeltaBuilder.this.delta.changed(javaElement, IJavaElementDelta.F_CATEGORIES);
							}
						}
					} else if (newTypeCategories != null) {
						Iterator elements = newTypeCategories.keySet().iterator();
						while (elements.hasNext()) {
							IJavaElement javaElement = (IJavaElement) elements.next();
							JavaElementDeltaBuilder.this.delta.changed(javaElement, IJavaElementDelta.F_CATEGORIES); // all categories for this element were removed
						}
					}
				}
			}
		}

		private void findAnnotationChanges(IAnnotation[] oldAnnotations, IAnnotation[] newAnnotations, IJavaElement parent) {
			ArrayList annotationDeltas = null;
			for (int i = 0, length = newAnnotations.length; i < length; i++) {
				IAnnotation newAnnotation = newAnnotations[i];
				Object oldInfo = this.annotationInfos.remove(newAnnotation);
				if (oldInfo == null) {
					JavaElementDelta annotationDelta = new JavaElementDelta(newAnnotation);
					annotationDelta.added();
					if (annotationDeltas == null) annotationDeltas = new ArrayList();
					annotationDeltas.add(annotationDelta);
					continue;
				} else {
					AnnotationInfo newInfo = null;
					try {
						newInfo = (AnnotationInfo) ((JavaElement) newAnnotation).getElementInfo();
					} catch (JavaModelException npe) {
						return;
					}
					if (!Util.equalArraysOrNull(((AnnotationInfo) oldInfo).members, newInfo.members)) {
						JavaElementDelta annotationDelta = new JavaElementDelta(newAnnotation);
						annotationDelta.changed(IJavaElementDelta.F_CONTENT);
						if (annotationDeltas == null) annotationDeltas = new ArrayList();
						annotationDeltas.add(annotationDelta);
					}
				}
			}
			for (int i = 0, length = oldAnnotations.length; i < length; i++) {
				IAnnotation oldAnnotation = oldAnnotations[i];
				if (this.annotationInfos.remove(oldAnnotation) != null) {
					JavaElementDelta annotationDelta = new JavaElementDelta(oldAnnotation);
					annotationDelta.removed();
					if (annotationDeltas == null) annotationDeltas = new ArrayList();
					annotationDeltas.add(annotationDelta);		}
			}
			if (annotationDeltas == null)
				return;
			int size = annotationDeltas.size();
			if (size > 0) {
				JavaElementDelta parentDelta = JavaElementDeltaBuilder.this.delta.changed(parent, IJavaElementDelta.F_ANNOTATIONS);
				parentDelta.annotationDeltas = (IJavaElementDelta[]) annotationDeltas.toArray(new IJavaElementDelta[size]);
			}
		}

		private boolean equals(char[][][] first, char[][][] second) {
			if (first == second)
				return true;
			if (first == null || second == null)
				return false;
			if (first.length != second.length)
				return false;
		
			for (int i = first.length; --i >= 0;)
				if (!CharOperation.equals(first[i], second[i]))
					return false;
			return true;
		}
	};

/**
 * Creates a java element comparator on a java element
 * looking as deep as necessary.
 */
public JavaElementDeltaBuilder(IJavaElement javaElement) {
	this(javaElement, Integer.MAX_VALUE);
}
/**
 * Creates a java element comparator on a java element
 * looking only 'maxDepth' levels deep.
 */
public JavaElementDeltaBuilder(IJavaElement javaElement, int maxDepth) {
	this.delta = new JavaElementDelta(javaElement);
	// if building a delta on a compilation unit or below,
	// it's a fine grained delta
	if (javaElement.getElementType() >= IJavaElement.COMPILATION_UNIT) {
		this.delta.fineGrained();
	}
	this.recorder.beginRecording((IElement) javaElement, new ElementDelta.Builder(this.delta), maxDepth);
}
public void buildDeltas() {
	this.recorder.endRecording();
	if (this.delta.getAffectedChildren().length == 0) {
		// this is a fine grained but not children affected -> mark as content changed
		this.delta.contentChanged();
	}
}
}
