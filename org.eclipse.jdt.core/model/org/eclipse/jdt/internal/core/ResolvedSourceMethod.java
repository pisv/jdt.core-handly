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

/**
 * Handle representing a source method that is resolved.
 * The uniqueKey contains the genericSignature of the resolved method. Use BindingKey to decode it.
 */
public class ResolvedSourceMethod extends SourceMethod {

	private String uniqueKey;

	/*
	 * See class comments.
	 */
	public ResolvedSourceMethod(JavaElement parent, String name, String[] parameterTypes, String uniqueKey) {
		super(parent, name, parameterTypes);
		this.uniqueKey = uniqueKey;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.SourceMethod#getKey()
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
	 * @see org.eclipse.jdt.core.IMethod#isResolved()
	 */
	public boolean isResolved() {
		return true;
	}

	public JavaElement unresolved() {
		SourceRefElement handle = new SourceMethod(this.parent, this.name, this.parameterTypes);
		handle.occurrenceCount = this.occurrenceCount;
		return handle;
	}
}
