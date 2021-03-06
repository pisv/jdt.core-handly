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

import static org.eclipse.handly.context.Contexts.*;

import org.eclipse.handly.context.IContext;
import org.eclipse.handly.util.ToStringOptions;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.internal.core.util.MementoTokenizer;

/**
 * @see IImportContainer
 */
public class ImportContainer extends SourceRefElement implements IImportContainer {
protected ImportContainer(CompilationUnit parent) {
	super(parent);
}
/**
 * @see IJavaElement
 */
public int getElementType() {
	return IMPORT_CONTAINER;
}
/*
 * @see JavaElement
 */
public IJavaElement getHandleFromMemento(String token, MementoTokenizer memento, WorkingCopyOwner workingCopyOwner) {
	switch (token.charAt(0)) {
		case JEM_COUNT:
			return getHandleUpdatingCountFromMemento(memento, workingCopyOwner);
		case JEM_IMPORTDECLARATION:
			if (memento.hasMoreTokens()) {
				String importName = memento.nextToken();
				JavaElement importDecl = (JavaElement)getImport(importName);
				return importDecl.getHandleFromMemento(memento, workingCopyOwner);
			} else {
				return this;
			}
	}
	return null;
}
/**
 * @see JavaElement#getHandleMemento()
 */
protected char getHandleMementoDelimiter() {
	return JavaElement.JEM_IMPORTDECLARATION;
}
/**
 * @see IImportContainer
 */
public IImportDeclaration getImport(String importName) {
	int index = importName.indexOf(".*"); ///$NON-NLS-1$
	boolean isOnDemand = index != -1;
	if (isOnDemand)
		// make sure to copy the string (so that it doesn't hold on the underlying char[] that might be much bigger than necessary)
		importName = new String(importName.substring(0, index));
	return getImport(importName, isOnDemand);
}
protected IImportDeclaration getImport(String importName, boolean isOnDemand) {
	return new ImportDeclaration(this, importName, isOnDemand);
}
public ISourceRange getNameRange() {
	return null;
}
/*
 * @see JavaElement#getPrimaryElement(boolean)
 */
public IJavaElement getPrimaryElement(boolean checkOwner) {
	CompilationUnit cu = (CompilationUnit)this.parent;
	if (checkOwner && cu.isPrimary()) return this;
	return cu.getImportContainer();
}
/**
 * @see ISourceReference
 */
public ISourceRange getSourceRange() throws JavaModelException {
	IJavaElement[] imports= getChildren();
	ISourceRange firstRange= ((ISourceReference)imports[0]).getSourceRange();
	ISourceRange lastRange= ((ISourceReference)imports[imports.length - 1]).getSourceRange();
	SourceRange range= new SourceRange(firstRange.getOffset(), lastRange.getOffset() + lastRange.getLength() - firstRange.getOffset());
	return range;
}
/**
 */
public String readableName() {

	return null;
}
@Override
public String toString_(IContext context) {
	ToStringOptions.FormatStyle style = context.getOrDefault(ToStringOptions.FORMAT_STYLE);
	if (style == ToStringOptions.FormatStyle.FULL || style == ToStringOptions.FormatStyle.LONG)
	{
		StringBuilder builder = new StringBuilder();
		toStringChildren_(builder, peekAtBody_(), with(of(ToStringOptions.FORMAT_STYLE,
			ToStringOptions.FormatStyle.SHORT), context));
		return builder.toString();
	}
	return super.toString_(context);
}
@Override
public void toStringName_(StringBuilder builder, IContext context) {
	builder.append("<import container>"); //$NON-NLS-1$
}
}
