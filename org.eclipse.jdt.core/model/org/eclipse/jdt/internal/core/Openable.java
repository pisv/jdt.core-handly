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

import java.util.Enumeration;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.PerformanceStats;
import org.eclipse.handly.context.IContext;
import org.eclipse.handly.util.Property;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.codeassist.SelectionEngine;
import org.eclipse.jdt.internal.core.util.Util;


/**
 * Abstract class for implementations of java elements which are IOpenable.
 *
 * @see IJavaElement
 * @see IOpenable
 */
@SuppressWarnings({"rawtypes"})
public abstract class Openable extends JavaElement implements IOpenable, IBufferChangedListener {

protected static final Property<Boolean> IS_STRUCTURE_KNOWN = Property.get(
	Openable.class.getName() + ".isStructureKnown", Boolean.class); //$NON-NLS-1$

protected Openable(JavaElement parent) {
	super(parent);
}
/**
 * The buffer associated with this element has changed. Registers
 * this element as being out of synch with its buffer's contents.
 * If the buffer has been closed, this element is set as NOT out of
 * synch with the contents.
 *
 * @see IBufferChangedListener
 */
public void bufferChanged(BufferChangedEvent event) {
	if (event.getBuffer().isClosed()) {
		getJavaModelManager().getElementsOutOfSynchWithBuffers().remove(this);
		getBufferManager().removeBuffer(event.getBuffer());
	} else {
		getJavaModelManager().getElementsOutOfSynchWithBuffers().add(this);
	}
}
/*
 * Returns whether this element can be removed from the Java model cache to make space.
 */
public boolean canBeRemovedFromCache() {
	try {
		return !hasUnsavedChanges();
	} catch (JavaModelException e) {
		return false;
	}
}
/*
 * Returns whether the buffer of this element can be removed from the Java model cache to make space.
 */
public boolean canBufferBeRemovedFromCache(IBuffer buffer) {
	return !buffer.hasUnsavedChanges();
}
@Override
public void close_(IContext context) {
	CloseHint hint = context.get(CLOSE_HINT);
	if (hint == CloseHint.CACHE_OVERFLOW && !canBeRemovedFromCache())
		return;
	super.close_(context);
}
/**
 * Close the buffer associated with this element, if any.
 */
protected void closeBuffer() {
	if (!hasBuffer()) return; // nothing to do
	IBuffer buffer = getBufferManager().getBuffer(this);
	if (buffer != null) {
		buffer.close();
		buffer.removeBufferChangedListener(this);
	}
}
protected void codeComplete(
		org.eclipse.jdt.internal.compiler.env.ICompilationUnit cu,
		org.eclipse.jdt.internal.compiler.env.ICompilationUnit unitToSkip,
		int position, CompletionRequestor requestor,
		WorkingCopyOwner owner,
		ITypeRoot typeRoot,
		IProgressMonitor monitor) throws JavaModelException {
	if (requestor == null) {
		throw new IllegalArgumentException("Completion requestor cannot be null"); //$NON-NLS-1$
	}
	PerformanceStats performanceStats = CompletionEngine.PERF
		? PerformanceStats.getStats(JavaModelManager.COMPLETION_PERF, this)
		: null;
	if(performanceStats != null) {
		performanceStats.startRun(new String(cu.getFileName()) + " at " + position); //$NON-NLS-1$
	}
	IBuffer buffer = getBuffer();
	if (buffer == null) {
		return;
	}
	if (position < -1 || position > buffer.getLength()) {
		throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS));
	}
	JavaProject project = (JavaProject) getJavaProject();
	SearchableEnvironment environment = project.newSearchableNameEnvironment(owner);

	// set unit to skip
	environment.unitToSkip = unitToSkip;

	// code complete
	CompletionEngine engine = new CompletionEngine(environment, requestor, project.getOptions(true), project, owner, monitor);
	engine.complete(cu, position, 0, typeRoot);
	if(performanceStats != null) {
		performanceStats.endRun();
	}
	if (NameLookup.VERBOSE) {
		System.out.println(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInSourcePackage: " + environment.nameLookup.timeSpentInSeekTypesInSourcePackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInBinaryPackage: " + environment.nameLookup.timeSpentInSeekTypesInBinaryPackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
	}
}
protected IJavaElement[] codeSelect(org.eclipse.jdt.internal.compiler.env.ICompilationUnit cu, int offset, int length, WorkingCopyOwner owner) throws JavaModelException {
	PerformanceStats performanceStats = SelectionEngine.PERF
		? PerformanceStats.getStats(JavaModelManager.SELECTION_PERF, this)
		: null;
	if(performanceStats != null) {
		performanceStats.startRun(new String(cu.getFileName()) + " at [" + offset + "," + length + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	JavaProject project = (JavaProject)getJavaProject();
	SearchableEnvironment environment = project.newSearchableNameEnvironment(owner);

	SelectionRequestor requestor= new SelectionRequestor(environment.nameLookup, this);
	IBuffer buffer = getBuffer();
	if (buffer == null) {
		return requestor.getElements();
	}
	int end= buffer.getLength();
	if (offset < 0 || length < 0 || offset + length > end ) {
		throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS));
	}

	// fix for 1FVXGDK
	SelectionEngine engine = new SelectionEngine(environment, requestor, project.getOptions(true), owner);
	engine.select(cu, offset, offset + length - 1);

	if(performanceStats != null) {
		performanceStats.endRun();
	}
	if (NameLookup.VERBOSE) {
		System.out.println(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInSourcePackage: " + environment.nameLookup.timeSpentInSeekTypesInSourcePackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println(Thread.currentThread() + " TIME SPENT in NameLoopkup#seekTypesInBinaryPackage: " + environment.nameLookup.timeSpentInSeekTypesInBinaryPackage + "ms");  //$NON-NLS-1$ //$NON-NLS-2$
	}
	return requestor.getElements();
}
@Override
public boolean exists_() {
	if (findBody_() != null)
		return true;
	switch (getElementType()) {
		case IJavaElement.PACKAGE_FRAGMENT:
			PackageFragmentRoot root = getPackageFragmentRoot();
			if (root.isArchive()) {
				// pkg in a jar -> need to open root to know if this pkg exists
				JarPackageFragmentRootInfo rootInfo;
				try {
					rootInfo = (JarPackageFragmentRootInfo) root.getElementInfo();
				} catch (JavaModelException e) {
					return false;
				}
				return rootInfo.rawPackageInfo.containsKey(((PackageFragment) this).names);
			}
			break;
		case IJavaElement.CLASS_FILE:
			if (getPackageFragmentRoot().isArchive()) {
				// class file in a jar -> need to open this class file to know if it exists
				return super.exists_();
			}
			break;
	}
	return super.exists_();
}
public String findRecommendedLineSeparator() throws JavaModelException {
	IBuffer buffer = getBuffer();
	String source = buffer == null ? null : buffer.getContents();
	return Util.getLineSeparator(source, getJavaProject());
}
/**
 * Note: a buffer with no unsaved changes can be closed by the Java Model
 * since it has a finite number of buffers allowed open at one time. If this
 * is the first time a request is being made for the buffer, an attempt is
 * made to create and fill this element's buffer. If the buffer has been
 * closed since it was first opened, the buffer is re-created.
 *
 * @see IOpenable
 */
public IBuffer getBuffer() throws JavaModelException {
	if (hasBuffer()) {
		// ensure element is open
		Object info = getElementInfo();
		IBuffer buffer = getBufferManager().getBuffer(this);
		if (buffer == null) {
			// try to (re)open a buffer
			buffer = openBuffer(null, info);
		}
		if (buffer instanceof NullBuffer) {
			return null;
		}
		return buffer;
	} else {
		return null;
	}
}
/**
 * Answers the buffer factory to use for creating new buffers
 * @deprecated
 */
public IBufferFactory getBufferFactory(){
	return getBufferManager().getDefaultBufferFactory();
}
/**
 * Returns the buffer manager for this element.
 */
protected BufferManager getBufferManager() {
	return BufferManager.getDefaultBufferManager();
}
/**
 * Return my underlying resource. Elements that may not have a
 * corresponding resource must override this method.
 *
 * @see IJavaElement
 */
public IResource getCorrespondingResource() throws JavaModelException {
	return getUnderlyingResource();
}

/*
 * @see IJavaElement
 */
public IOpenable getOpenable() {
	return this;
}
/**
 * Find enclosing package fragment root if any
 */
public PackageFragmentRoot getPackageFragmentRoot() {
	return (PackageFragmentRoot) getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
}
public IResource getResource() {
	PackageFragmentRoot root = getPackageFragmentRoot();
	if (root != null) {
		if (root.isExternal())
			return null;
		if (root.isArchive())
			return root.resource(root);
	}
	return resource(root);
}



/**
 * @see IJavaElement
 */
public IResource getUnderlyingResource() throws JavaModelException {
	IResource parentResource = this.parent.getUnderlyingResource();
	if (parentResource == null) {
		return null;
	}
	int type = parentResource.getType();
	if (type == IResource.FOLDER || type == IResource.PROJECT) {
		IContainer folder = (IContainer) parentResource;
		IResource resource = folder.findMember(getElementName());
		if (resource == null) {
			throw newNotPresentException();
		} else {
			return resource;
		}
	} else {
		return parentResource;
	}
}

/**
 * Returns true if this element may have an associated source buffer,
 * otherwise false. Subclasses must override as required.
 */
protected boolean hasBuffer() {
	return false;
}
/**
 * @see IOpenable
 */
public boolean hasUnsavedChanges() throws JavaModelException{

	if (isReadOnly() || !isOpen()) {
		return false;
	}
	IBuffer buf = getBuffer();
	if (buf != null && buf.hasUnsavedChanges()) {
		return true;
	}
	// for package fragments, package fragment roots, and projects must check open buffers
	// to see if they have an child with unsaved changes
	int elementType = getElementType();
	if (elementType == PACKAGE_FRAGMENT ||
		elementType == PACKAGE_FRAGMENT_ROOT ||
		elementType == JAVA_PROJECT ||
		elementType == JAVA_MODEL) { // fix for 1FWNMHH
		Enumeration openBuffers= getBufferManager().getOpenBuffers();
		while (openBuffers.hasMoreElements()) {
			IBuffer buffer= (IBuffer)openBuffers.nextElement();
			if (buffer.hasUnsavedChanges()) {
				IJavaElement owner= (IJavaElement)buffer.getOwner();
				if (isAncestorOf(owner)) {
					return true;
				}
			}
		}
	}

	return false;
}
/**
 * Subclasses must override as required.
 *
 * @see IOpenable
 */
public boolean isConsistent() {
	return true;
}
/**
 *
 * @see IOpenable
 */
public boolean isOpen() {
	return findBody_() != null;
}
/**
 * Returns true if this represents a source element.
 * Openable source elements have an associated buffer created
 * when they are opened.
 */
protected boolean isSourceElement() {
	return false;
}
/**
 * @see IJavaElement
 */
public boolean isStructureKnown() throws JavaModelException {
	return ((OpenableElementInfo)getElementInfo()).isStructureKnown();
}
/**
 * @see IOpenable
 */
public void makeConsistent(IProgressMonitor monitor) throws JavaModelException {
	// only compilation units can be inconsistent
	// other openables cannot be inconsistent so default is to do nothing
}
/**
 * @see IOpenable
 */
public void open(IProgressMonitor pm) throws JavaModelException {
	getElementInfo(pm);
}

@Override
public Object open_(IContext context, IProgressMonitor monitor) throws CoreException {
	if (JavaModelCache.VERBOSE){
		String element;
		switch (getElementType()) {
			case JAVA_PROJECT:
				element = "project"; //$NON-NLS-1$
				break;
			case PACKAGE_FRAGMENT_ROOT:
				element = "root"; //$NON-NLS-1$
				break;
			case PACKAGE_FRAGMENT:
				element = "package"; //$NON-NLS-1$
				break;
			case CLASS_FILE:
				element = "class file"; //$NON-NLS-1$
				break;
			case COMPILATION_UNIT:
				element = "compilation unit"; //$NON-NLS-1$
				break;
			default:
				element = "element"; //$NON-NLS-1$
		}
		System.out.println(Thread.currentThread() +" OPENING " + element + " " + this.toStringWithAncestors()); //$NON-NLS-1$//$NON-NLS-2$
	}

	Object body = super.open_(context, monitor);

	// remove out of sync buffer for this element
	getJavaModelManager().getElementsOutOfSynchWithBuffers().remove(this);

	if (JavaModelCache.VERBOSE) {
		System.out.println(getJavaModelManager().cacheToString("-> ")); //$NON-NLS-1$
	}

	return body;
}

/**
 * Opens a buffer on the contents of this element, and returns
 * the buffer, or returns <code>null</code> if opening fails.
 * By default, do nothing - subclasses that have buffers
 * must override as required.
 */
protected IBuffer openBuffer(IProgressMonitor pm, Object info) throws JavaModelException {
	return null;
}

@Override
public void removing_(Object body) {
	closeBuffer();
	super.removing_(body);
}

public IResource resource() {
	PackageFragmentRoot root = getPackageFragmentRoot();
	if (root != null && root.isArchive())
		return root.resource(root);
	return resource(root);
}

protected abstract IResource resource(PackageFragmentRoot root);

/**
 * Returns whether the corresponding resource or associated file exists
 */
protected boolean resourceExists(IResource underlyingResource) {
	return underlyingResource.isAccessible();
}

/**
 * @see IOpenable
 */
public void save(IProgressMonitor pm, boolean force) throws JavaModelException {
	if (isReadOnly()) {
		throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.READ_ONLY, this));
	}
	IBuffer buf = getBuffer();
	if (buf != null) { // some Openables (like a JavaProject) don't have a buffer
		buf.save(pm, force);
		makeConsistent(pm); // update the element info of this element
	}
}

}
