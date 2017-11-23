/*******************************************************************************
 * Copyright (c) 2017 1C-Soft LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vladimir Piskarev (1C) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import org.eclipse.handly.model.impl.support.ElementManager;
import org.eclipse.jdt.core.IJavaElement;

public class JavaElementManager extends ElementManager {

	public JavaElementManager() {
		super(new JavaModelCache());
	}

	public synchronized String cacheToString(String prefix) {
		return getJavaModelCache().toStringFillingRatio(prefix);
	}

	/**
	 * Returns the existing element in the cache that is equal to the given element.
	 */
	public synchronized IJavaElement getExistingElement(IJavaElement element) {
		return getJavaModelCache().getExistingElement(element);
	}

	public synchronized int getOpenableCacheSize() {
		return getJavaModelCache().openableCache.getSpaceLimit();
	}

	/*
	 * Remember the info for the jar binary type
	 */
	synchronized void putJarTypeInfo(IJavaElement type, Object info) {
		getJavaModelCache().jarTypeCache.put(type, info);
	}

	synchronized void removeFromJarTypeCache(BinaryType type) {
		getJavaModelCache().removeFromJarTypeCache(type);
	}

	/*
	 * Resets the cache that holds on binary type in jar files
	 */
	synchronized void resetJarTypeCache() {
		getJavaModelCache().resetJarTypeCache();
	}

	private JavaModelCache getJavaModelCache() {
		return (JavaModelCache) this.cache;
	}
}
