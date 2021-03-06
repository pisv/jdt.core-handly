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

import java.util.Map;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.core.Initializer;
import org.eclipse.jdt.internal.core.JavaElement;

@SuppressWarnings("rawtypes")
public class AssistInitializer extends Initializer {
	private Map bindingCache;
	private Map infoCache;
	public AssistInitializer(JavaElement parent, int count, Map bindingCache, Map infoCache) {
		super(parent, count);
		this.bindingCache = bindingCache;
		this.infoCache = infoCache;
	}

	@Override
	public Object findBody_() {
		return this.infoCache.get(this);
	}

	public IType getType(String typeName, int count) {
		AssistSourceType type = new AssistSourceType(this, typeName, this.bindingCache, this.infoCache);
		type.occurrenceCount = count;
		return type;
	}
}
