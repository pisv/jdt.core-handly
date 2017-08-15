/*******************************************************************************
 * Copyright (c) 2014, 2017 IBM Corporation and others.
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

public class ResolvedLambdaExpression extends LambdaExpression {

	private String uniqueKey;
	LambdaExpression unresolved;

	public ResolvedLambdaExpression(JavaElement parent, LambdaExpression unresolved, String uniqueKey) {
		super(parent, unresolved.interphase, unresolved.sourceStart, unresolved.sourceEnd, unresolved.arrowPosition, unresolved.lambdaMethod);
		this.uniqueKey = uniqueKey;
		this.unresolved = unresolved;
	}

	@Override
	public boolean _canEqual(Object obj) {
		return obj instanceof ResolvedLambdaExpression;
	}

	@Override
	public void _toStringBody(StringBuilder builder, Object body, IContext context) {
		super._toStringBody(builder, body, context);
		if (context.getOrDefault(SHOW_RESOLVED_INFO)) {
			builder.append(" {key="); //$NON-NLS-1$
			builder.append(this.getKey());
			builder.append("}"); //$NON-NLS-1$
		}
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
	public boolean equals(Object o) {
		return this.unresolved.equals(o);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.SourceType#isResolved()
	 */
	public boolean isResolved() {
		return true;
	}

	public JavaElement unresolved() {
		return this.unresolved;
	}
}