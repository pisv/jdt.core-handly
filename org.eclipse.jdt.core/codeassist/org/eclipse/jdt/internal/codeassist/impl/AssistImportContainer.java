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

import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.ImportContainer;

@SuppressWarnings("rawtypes")
public class AssistImportContainer extends ImportContainer {
	private Map infoCache;
	public AssistImportContainer(CompilationUnit parent, Map infoCache) {
		super(parent);
		this.infoCache = infoCache;
	}

	protected IImportDeclaration getImport(String importName, boolean isOnDemand) {
		return new AssistImportDeclaration(this, importName, isOnDemand, this.infoCache);
	}

	@Override
	public Object hFindBody() {
		return this.infoCache.get(this);
	}
}
