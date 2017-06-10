/*******************************************************************************
 * Copyright (c) 2004, 2017 IBM Corporation and others.
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
import org.eclipse.jdt.core.JavaModelException;

/**
 * Handle representing a source type that is resolved.
 * The uniqueKey contains the genericTypeSignature of the resolved type. Use BindingKey to decode it.
 */
public class ResolvedSourceType extends SourceType {

	private String uniqueKey;

	/*
	 * See class comments.
	 */
	public ResolvedSourceType(JavaElement parent, String name, String uniqueKey) {
		super(parent, name);
		this.uniqueKey = uniqueKey;
	}

	public String getFullyQualifiedParameterizedName() throws JavaModelException {
		return getFullyQualifiedParameterizedName(getFullyQualifiedName('.'), this.uniqueKey);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.SourceType#getKey()
	 */
	public String getKey() {
		return this.uniqueKey;
	}

	@Override
	public void hToStringBody(StringBuilder builder, Object body, IContext context) {
		super.hToStringBody(builder, body, context);
		if (context.getOrDefault(SHOW_RESOLVED_INFO)) {
			builder.append(" {key="); //$NON-NLS-1$
			builder.append(this.getKey());
			builder.append("}"); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.SourceType#isResolved()
	 */
	public boolean isResolved() {
		return true;
	}

	public JavaElement unresolved() {
		SourceType handle = new SourceType(this.parent, this.name);
		handle.occurrenceCount = this.occurrenceCount;
		handle.localOccurrenceCount = this.localOccurrenceCount;
		return handle;
	}
}
