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

public class SourceFieldWithChildrenInfo extends SourceFieldElementInfo {

	protected JavaElement[] children;
	
	public SourceFieldWithChildrenInfo(JavaElement[] children) {
		this.children = children;
	}

	public JavaElement[] getChildren() {
		return this.children;
	}

}
