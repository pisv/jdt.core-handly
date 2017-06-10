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

import org.eclipse.handly.context.IContext;
import org.eclipse.jdt.core.*;

/**
 * @see IPackageDeclaration
 */

public class PackageDeclaration extends SourceRefElement implements IPackageDeclaration {

	String name;

protected PackageDeclaration(CompilationUnit parent, String name) {
	super(parent);
	this.name = name;
}
public String getElementName() {
	return this.name;
}
/**
 * @see IJavaElement
 */
public int getElementType() {
	return PACKAGE_DECLARATION;
}
/**
 * @see JavaElement#getHandleMemento()
 */
protected char getHandleMementoDelimiter() {
	return JavaElement.JEM_PACKAGEDECLARATION;
}
/**
 * @see IPackageDeclaration#getNameRange()
 */
public ISourceRange getNameRange() throws JavaModelException {
	AnnotatableInfo info = (AnnotatableInfo) getElementInfo();
	return info.getNameRange();
}
/*
 * @see JavaElement#getPrimaryElement(boolean)
 */
public IJavaElement getPrimaryElement(boolean checkOwner) {
	CompilationUnit cu = (CompilationUnit)getAncestor(COMPILATION_UNIT);
	if (checkOwner && cu.isPrimary()) return this;
	return cu.getPackageDeclaration(this.name);
}
@Override
public void hToStringName(StringBuilder builder, IContext context) {
	builder.append("package "); //$NON-NLS-1$
	super.hToStringName(builder, context);
}
}
