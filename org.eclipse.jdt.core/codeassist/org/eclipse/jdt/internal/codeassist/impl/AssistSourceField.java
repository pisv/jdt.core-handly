/*******************************************************************************
 * Copyright (c) 2008, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.codeassist.impl;

import static org.eclipse.handly.context.Contexts.of;
import static org.eclipse.handly.context.Contexts.with;

import java.util.Map;

import org.eclipse.handly.context.IContext;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.ResolvedSourceField;

@SuppressWarnings("rawtypes")
public class AssistSourceField extends ResolvedSourceField {
	private Map bindingCache;
	private Map infoCache;

	private String uniqueKey;
	private boolean isResolved;

	public AssistSourceField(JavaElement parent, String name, Map bindingCache, Map infoCache) {
		super(parent, name, null);
		this.bindingCache = bindingCache;
		this.infoCache = infoCache;
	}

	@Override
	public Object findBody_() {
		return this.infoCache.get(this);
	}

	public IAnnotation getAnnotation(String annotationName) {
		return new AssistAnnotation(this, annotationName, this.infoCache);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.SourceField#getKey()
	 */
	public String getKey() {
		if (this.uniqueKey == null) {
			Binding binding = (Binding) this.bindingCache.get(this);
			if (binding != null) {
				this.isResolved = true;
				this.uniqueKey = new String(binding.computeUniqueKey());
			} else {
				this.isResolved = false;
				try {
					this.uniqueKey = getKey(this, false/*don't open*/);
				} catch (JavaModelException e) {
					// happen only if force open is true
					return null;
				}
			}
		}
		return this.uniqueKey;
	}

	public IType getType(String typeName, int count) {
		AssistSourceType type = new AssistSourceType(this, typeName, this.bindingCache, this.infoCache);
		type.occurrenceCount = count;
		return type;
	}

	public boolean isResolved() {
		getKey();
		return this.isResolved;
	}

	@Override
	public void toStringBody_(StringBuilder builder, Object body, IContext context) {
		super.toStringBody_(builder, body, with(of(SHOW_RESOLVED_INFO, context.getOrDefault(SHOW_RESOLVED_INFO) && isResolved()), context));
	}
}
