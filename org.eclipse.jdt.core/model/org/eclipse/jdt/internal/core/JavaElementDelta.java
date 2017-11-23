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

import static org.eclipse.handly.context.Contexts.*;
import static org.eclipse.handly.util.ToStringOptions.*;
import static org.eclipse.handly.util.ToStringOptions.FormatStyle.*;

import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.handly.context.IContext;
import org.eclipse.handly.model.Elements;
import org.eclipse.handly.model.IElement;
import org.eclipse.handly.model.IElementDeltaConstants;
import org.eclipse.handly.model.impl.support.ElementDelta;
import org.eclipse.handly.util.IndentPolicy;
import org.eclipse.handly.util.Property;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * @see IJavaElementDelta
 */
public class JavaElementDelta extends ElementDelta implements IJavaElementDelta, ISimpleDelta {

	public static class Flags {
		public static long translate(int javaFlags) {
			int genericFlags = 0;
			if ((javaFlags & F_CONTENT) != 0)
				genericFlags |= IElementDeltaConstants.F_CONTENT;
			if ((javaFlags & F_CHILDREN) != 0)
				genericFlags |= IElementDeltaConstants.F_CHILDREN;
			if ((javaFlags & F_FINE_GRAINED) != 0)
				genericFlags |= IElementDeltaConstants.F_FINE_GRAINED;
			if ((javaFlags & F_MOVED_FROM) != 0)
				genericFlags |= IElementDeltaConstants.F_MOVED_FROM;
			if ((javaFlags & F_MOVED_TO) != 0)
				genericFlags |= IElementDeltaConstants.F_MOVED_TO;
			if ((javaFlags & (F_OPENED | F_CLOSED)) != 0)
				genericFlags |= IElementDeltaConstants.F_OPEN;
			if ((javaFlags & F_REORDER) != 0)
				genericFlags |= IElementDeltaConstants.F_REORDER;
			if ((javaFlags & F_PRIMARY_RESOURCE) != 0)
				genericFlags |= IElementDeltaConstants.F_UNDERLYING_RESOURCE;
			if ((javaFlags & F_PRIMARY_WORKING_COPY) != 0)
				genericFlags |= IElementDeltaConstants.F_WORKING_COPY;
			return ((long) javaFlags) << 32 | genericFlags;
		}
		private Flags() {
		}
	}

	/**
	 * Empty array of JavaElementDelta
	 */
	private static final JavaElementDelta[] EMPTY_DELTA= new JavaElementDelta[] {};

	public static final IndentPolicy DELTA_INDENT_POLICY = new IndentPolicy() {
		@Override
		public void appendIndent(StringBuilder builder) {
			builder.append('\t');
		}
	};
	
	public static final Property<Util.Comparer> COMPARER = Property.get(JavaElementDelta.class.getName() + ".comparer", Util.Comparer.class); //$NON-NLS-1$

	/*
 * The AST created during the last reconcile operation.
 * Non-null only iff:
 * - in a POST_RECONCILE event
 * - an AST was requested during the last reconcile operation
 * - the changed element is an ICompilationUnit in working copy mode
 */
CompilationUnit ast = null;
IJavaElementDelta[] annotationDeltas = EMPTY_DELTA;
/**
 * Creates the root delta. To create the nested delta
 * hierarchies use the following convenience methods. The root
 * delta can be created at any level (for example: project, package root,
 * package fragment...).
 * <ul>
 * <li><code>added(IJavaElement)</code>
 * <li><code>changed(IJavaElement)</code>
 * <li><code>moved(IJavaElement, IJavaElement)</code>
 * <li><code>removed(IJavaElement)</code>
 * <li><code>renamed(IJavaElement, IJavaElement)</code>
 * </ul>
 */
public JavaElementDelta(IJavaElement element) {
	super((IElement) element);
	setAffectedChildren_(EMPTY_DELTA);
}
public void added() {
	setKind_(IElementDeltaConstants.ADDED);
}
/**
 * Creates the nested deltas resulting from an add operation.
 * Convenience method for creating add deltas.
 * The constructor should be used to create the root delta
 * and then an add operation should call this method.
 */
public void added(IJavaElement element) {
	added(element, 0);
}
public void added(IJavaElement element, int flags) {
	JavaElementDelta addedDelta = new JavaElementDelta(element);
	addedDelta.added();
	addedDelta.setFlags_(Flags.translate(flags));
	insertDeltaTree(element, addedDelta);
}
protected void addResourceDelta(IResourceDelta child) {
	addResourceDelta_(child);
}
/**
 * Creates the nested deltas resulting from a change operation.
 * Convenience method for creating change deltas.
 * The constructor should be used to create the root delta
 * and then a change operation should call this method.
 */
public JavaElementDelta changed(IJavaElement element, int changeFlag) {
	JavaElementDelta changedDelta = new JavaElementDelta(element);
	changedDelta.changed(changeFlag);
	insertDeltaTree(element, changedDelta);
	return changedDelta;
}
public void changed(int flags) {
	setKind_(IElementDeltaConstants.CHANGED);
	setFlags_(getFlags_() | Flags.translate(flags));
}
/*
 * Records the last changed AST  .
 */
public void changedAST(CompilationUnit changedAST) {
	this.ast = changedAST;
	changed(F_AST_AFFECTED);
}
protected void clearAffectedChildren() {
	setAffectedChildren_(new ElementDelta[0]);
}
/**
 * Creates the nested deltas for a closed element.
 */
public void closed(IJavaElement element) {
	JavaElementDelta delta = new JavaElementDelta(element);
	delta.changed(F_CLOSED);
	insertDeltaTree(element, delta);
}
/**
 * Mark this delta as a content changed delta.
 */
public void contentChanged() {
	setFlags_(getFlags_() | IElementDeltaConstants.F_CONTENT);
}
@Override
protected void copyFrom_(ElementDelta delta, boolean init) {
	long oldFlags = getFlags_();
	super.copyFrom_(delta, init);
	if (init) {
		this.annotationDeltas = ((JavaElementDelta) delta).annotationDeltas;
	}
	else {
		if (((JavaElementDelta) delta).annotationDeltas.length > 0) {
			if (this.annotationDeltas.length > 0)
				throw new AssertionError(
						"Merge of annotaion deltas is not supported"); //$NON-NLS-1$

			this.annotationDeltas = ((JavaElementDelta) delta).annotationDeltas;
		}
		// update flags
		long flags = delta.getFlags_();
		if ((flags & IElementDeltaConstants.F_FINE_GRAINED) == 0
				&& (flags & IElementDeltaConstants.F_CONTENT) != 0
				&& (oldFlags & IElementDeltaConstants.F_FINE_GRAINED) != 0
				&& (oldFlags & IElementDeltaConstants.F_CONTENT) == 0) {
			// case of fine grained delta (this delta) and delta coming from
			// DeltaProcessor (delta): ensure F_CONTENT is not propagated from delta
			setFlags_(getFlags_() & ~IElementDeltaConstants.F_CONTENT);
		}
	}
}
protected JavaElementDelta find(IJavaElement e) {
	return (JavaElementDelta) findDelta_((IElement) e);
}
/**
 * Mark this delta as a fine-grained delta.
 */
public void fineGrained() {
	changed(F_FINE_GRAINED);
}

/**
 * @see IJavaElementDelta
 */
public IJavaElementDelta[] getAddedChildren() {
	return (IJavaElementDelta[]) getAddedChildren_();
}
/**
 * @see IJavaElementDelta
 */
public IJavaElementDelta[] getAffectedChildren() {
	return (IJavaElementDelta[]) getAffectedChildren_();
}
public IJavaElementDelta[] getAnnotationDeltas() {
	return this.annotationDeltas;
}
/**
 * @see IJavaElementDelta
 */
public IJavaElementDelta[] getChangedChildren() {
	return (IJavaElementDelta[]) getChangedChildren_();
}
public CompilationUnit getCompilationUnitAST() {
	return this.ast;
}
/**
 * @see IJavaElementDelta
 */
public IJavaElement getElement() {
	return (IJavaElement) getElement_();
}
/**
 * @see IJavaElementDelta
 */
public int getFlags() {
    return (int) (getFlags_() >> 32);
}
/**
 * @see IJavaElementDelta
 */
public int getKind() {
	int kind = getKind_();
	if (kind == IElementDeltaConstants.ADDED)
		return ADDED;
	else if (kind == IElementDeltaConstants.REMOVED)
		return REMOVED;
	else if (kind == IElementDeltaConstants.CHANGED)
		return CHANGED;
	return 0;
}
/**
 * @see IJavaElementDelta
 */
public IJavaElement getMovedFromElement() {
	return (IJavaElement) getMovedFromElement_();
}
/**
 * @see IJavaElementDelta
 */
public IJavaElement getMovedToElement() {
	return (IJavaElement) getMovedToElement_();
}
/**
 * @see IJavaElementDelta
 */
public IJavaElementDelta[] getRemovedChildren() {
	return (IJavaElementDelta[]) getRemovedChildren_();
}
/**
 * Return the collection of resource deltas. Return null if none.
 */
public IResourceDelta[] getResourceDeltas() {
	return getResourceDeltas_();
}
protected void insertDeltaTree(IJavaElement element, JavaElementDelta delta) {
	if (!Elements.equalsAndSameParentChain(getElement_(), delta.getElement_()))
		insertSubTree_(delta);
	else
		mergeWith_(delta);
}
/**
 * Creates the nested deltas resulting from an move operation.
 * Convenience method for creating the "move from" delta.
 * The constructor should be used to create the root delta
 * and then the move operation should call this method.
 */
public void movedFrom(IJavaElement movedFromElement, IJavaElement movedToElement) {
	JavaElementDelta removedDelta = new JavaElementDelta(movedFromElement);
	removedDelta.setKind_(IElementDeltaConstants.REMOVED);
	removedDelta.setFlags_(IElementDeltaConstants.F_MOVED_TO);
	removedDelta.setMovedToElement_((IElement) movedToElement);
	insertDeltaTree(movedFromElement, removedDelta);
}
/**
 * Creates the nested deltas resulting from an move operation.
 * Convenience method for creating the "move to" delta.
 * The constructor should be used to create the root delta
 * and then the move operation should call this method.
 */
public void movedTo(IJavaElement movedToElement, IJavaElement movedFromElement) {
	JavaElementDelta addedDelta = new JavaElementDelta(movedToElement);
	addedDelta.setKind_(IElementDeltaConstants.ADDED);
	addedDelta.setFlags_(IElementDeltaConstants.F_MOVED_FROM);
	addedDelta.setMovedFromElement_((IElement) movedFromElement);
	insertDeltaTree(movedToElement, addedDelta);
}
@Override
protected ElementDelta newDelta_(IElement element) {
	return new JavaElementDelta((IJavaElement) element);
}
/**
 * Creates the nested deltas for an opened element.
 */
public void opened(IJavaElement element) {
	JavaElementDelta delta = new JavaElementDelta(element);
	delta.changed(F_OPENED);
	insertDeltaTree(element, delta);
}
public void removed() {
	setKind_(IElementDeltaConstants.REMOVED);
	setFlags_(0);
}
/**
 * Creates the nested deltas resulting from an delete operation.
 * Convenience method for creating removed deltas.
 * The constructor should be used to create the root delta
 * and then the delete operation should call this method.
 */
public void removed(IJavaElement element) {
	removed(element, 0);
}
public void removed(IJavaElement element, int flags) {
	JavaElementDelta removedDelta = new JavaElementDelta(element);
	removedDelta.removed();
	removedDelta.setFlags_(Flags.translate(flags));
	insertDeltaTree(element, removedDelta);
}
@Override
protected void setFlags_(long flags) {
	flags = normalizeFlags(flags, IElementDeltaConstants.F_CONTENT, F_CONTENT);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_CHILDREN, F_CHILDREN);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_FINE_GRAINED, F_FINE_GRAINED);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_MOVED_FROM, F_MOVED_FROM);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_MOVED_TO, F_MOVED_TO);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_REORDER, F_REORDER);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_UNDERLYING_RESOURCE, F_PRIMARY_RESOURCE);
	flags = normalizeFlags(flags, IElementDeltaConstants.F_WORKING_COPY, F_PRIMARY_WORKING_COPY);
	if ((flags & IElementDeltaConstants.F_OPEN) != 0)
		flags |= shl32(getElement().getResource().isAccessible() ? F_OPENED : F_CLOSED);
	else
		flags &= ~shl32(F_OPENED | F_CLOSED);
	super.setFlags_(flags);
}
private static long normalizeFlags(long flags, long genericFlags, int javaFlags) {
	return (flags & genericFlags) != 0 ? (flags | shl32(javaFlags)) : (flags & ~shl32(javaFlags));
}
private static long shl32(int x) {
	return ((long) x) << 32;
}
/**
 * Creates the nested deltas resulting from a change operation.
 * Convenience method for creating change deltas.
 * The constructor should be used to create the root delta
 * and then a change operation should call this method.
 */
public void sourceAttached(IJavaElement element) {
	JavaElementDelta attachedDelta = new JavaElementDelta(element);
	attachedDelta.changed(F_SOURCEATTACHED);
	insertDeltaTree(element, attachedDelta);
}
/**
 * Creates the nested deltas resulting from a change operation.
 * Convenience method for creating change deltas.
 * The constructor should be used to create the root delta
 * and then a change operation should call this method.
 */
public void sourceDetached(IJavaElement element) {
	JavaElementDelta detachedDelta = new JavaElementDelta(element);
	detachedDelta.changed(F_SOURCEDETACHED);
	insertDeltaTree(element, detachedDelta);
}
public String toString() {
	return toString_(of(INDENT_POLICY, DELTA_INDENT_POLICY));
}
@Override
public String toString_(IContext context) {
	StringBuilder builder = new StringBuilder();
	builder.append(super.toString_(context));
	// process annotation deltas
	FormatStyle style = context.getOrDefault(FORMAT_STYLE);
	if (style == FULL || style == LONG) {
		IJavaElementDelta[] annotations = getAnnotationDeltas();
		Util.Comparer comparer = context.getOrDefault(COMPARER);
		if (comparer != null) {
			annotations = annotations.clone();
			Util.sort(annotations, comparer);
		}
		IndentPolicy indentPolicy = context.getOrDefault(INDENT_POLICY);
		int indentLevel = context.getOrDefault(INDENT_LEVEL);
		for (int i = 0; i < annotations.length; ++i) {
			indentPolicy.appendLine(builder);
			builder.append(((JavaElementDelta) annotations[i]).toString_(
					with(of(INDENT_LEVEL, indentLevel + 1), context)));
		}
	}
	return builder.toString();
}
@Override
public void toStringChildren_(StringBuilder builder, IContext context) {
	IndentPolicy indentPolicy = context.getOrDefault(INDENT_POLICY);
	ElementDelta[] affectedChildren = getAffectedChildren_();
	Util.Comparer comparer = context.getOrDefault(COMPARER);
	if (comparer != null) {
		affectedChildren = affectedChildren.clone();
		Util.sort(affectedChildren, comparer);
	}
	for (int i = 0; i < affectedChildren.length; i++)
	{
		if (i > 0)
			indentPolicy.appendLine(builder);
		builder.append(affectedChildren[i].toString_(context));
	}
}
@Override
protected boolean toStringFlags_(StringBuilder builder, IContext context) {
	boolean prev = false;
	int flags = getFlags();
	if ((flags & F_CHILDREN) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("CHILDREN"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_CONTENT) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("CONTENT"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_MOVED_FROM) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("MOVED_FROM(" + ((JavaElement)getMovedFromElement()).toStringWithAncestors() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		prev = true;
	}
	if ((flags & F_MOVED_TO) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("MOVED_TO(" + ((JavaElement)getMovedToElement()).toStringWithAncestors() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		prev = true;
	}
	if ((flags & F_ADDED_TO_CLASSPATH) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("ADDED TO CLASSPATH"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_REMOVED_FROM_CLASSPATH) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("REMOVED FROM CLASSPATH"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_REORDER) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("REORDERED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_ARCHIVE_CONTENT_CHANGED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("ARCHIVE CONTENT CHANGED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_SOURCEATTACHED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("SOURCE ATTACHED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_SOURCEDETACHED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("SOURCE DETACHED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_FINE_GRAINED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("FINE GRAINED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_PRIMARY_WORKING_COPY) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("PRIMARY WORKING COPY"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_CLASSPATH_CHANGED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("RAW CLASSPATH CHANGED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_RESOLVED_CLASSPATH_CHANGED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("RESOLVED CLASSPATH CHANGED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_PRIMARY_RESOURCE) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("PRIMARY RESOURCE"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_OPENED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("OPENED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_CLOSED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("CLOSED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_AST_AFFECTED) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("AST AFFECTED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_CATEGORIES) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("CATEGORIES"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_ANNOTATIONS) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("ANNOTATIONS"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_MODIFIERS) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("MODIFIERS CHANGED"); //$NON-NLS-1$
		prev = true;
	}
	if ((flags & F_SUPER_TYPES) != 0) {
		if (prev)
			builder.append(" | "); //$NON-NLS-1$
		builder.append("SUPER TYPES CHANGED"); //$NON-NLS-1$
		prev = true;
	}
	return prev;
}
}
