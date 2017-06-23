/*******************************************************************************
 * Copyright (c) 2000, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.handly.context.IContext;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * @see IInitializer
 */

public class Initializer extends Member implements IInitializer {

protected Initializer(JavaElement parent, int count) {
	super(parent);
	// 0 is not valid: this first occurrence is occurrence 1.
	if (count <= 0)
		throw new IllegalArgumentException();
	this.occurrenceCount = count;
}
/**
 * @see IJavaElement
 */
public int getElementType() {
	return INITIALIZER;
}
/**
 * @see JavaElement#getHandleMemento(StringBuffer)
 */
protected void getHandleMemento(StringBuffer buff) {
	((JavaElement)getParent()).getHandleMemento(buff);
	buff.append(getHandleMementoDelimiter());
	buff.append(this.occurrenceCount);
}
/**
 * @see JavaElement#getHandleMemento()
 */
protected char getHandleMementoDelimiter() {
	return JavaElement.JEM_INITIALIZER;
}
public int hashCode() {
	return Util.combineHashCodes(this.parent.hashCode(), this.occurrenceCount);
}
@Override
public void hToStringBody(StringBuilder builder, Object body, IContext context) {
	if (body == null) {
		builder.append("<initializer #"); //$NON-NLS-1$
		builder.append(this.occurrenceCount);
		builder.append("> (not open)"); //$NON-NLS-1$
	} else if (body == NO_BODY) {
		builder.append("<initializer #"); //$NON-NLS-1$
		builder.append(this.occurrenceCount);
		builder.append(">"); //$NON-NLS-1$
	} else {
		try {
			builder.append("<"); //$NON-NLS-1$
			if (Flags.isStatic(getFlags())) {
				builder.append("static "); //$NON-NLS-1$
			}
		builder.append("initializer #"); //$NON-NLS-1$
		builder.append(this.occurrenceCount);
		builder.append(">"); //$NON-NLS-1$
		} catch (JavaModelException e) {
			builder.append("<JavaModelException in toString of " + getElementName()); //$NON-NLS-1$
		}
	}
}
/**
 */
public String readableName() {

	return ((JavaElement)getDeclaringType()).readableName();
}
/**
 * @see ISourceManipulation
 */
public void rename(String newName, boolean force, IProgressMonitor monitor) throws JavaModelException {
	throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INVALID_ELEMENT_TYPES, this));
}
/**
 * @see IMember
 */
public ISourceRange getNameRange() {
	return null;
}
/*
 * @see JavaElement#getPrimaryElement(boolean)
 */
public IJavaElement getPrimaryElement(boolean checkOwner) {
	if (checkOwner) {
		CompilationUnit cu = (CompilationUnit)getAncestor(COMPILATION_UNIT);
		if (cu == null || cu.isPrimary()) return this;
	}
	IJavaElement primaryParent = this.parent.getPrimaryElement(false);
	return ((IType)primaryParent).getInitializer(this.occurrenceCount);
}
}
