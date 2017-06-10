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

import org.eclipse.jdt.core.IJavaElementDelta;

public interface ISimpleDelta {

	/*
	 * @see IJavaElementDelta#getFlags()
	 */
	int getFlags();

	/*
	 * @see IJavaElementDelta#getKind()
	 */
	int getKind();

	/*
	 * Marks this delta as added
	 */
	void added();

	/*
	 * Marks this delta as removed
	 */
	void removed();

	/*
	 * Marks this delta as changed with the given change flag
	 */
	void changed(int flags);

	/*
	 * Mark this delta has a having a modifiers change
	 */
	default void modifiers() {
		changed(IJavaElementDelta.F_MODIFIERS);
	}

	/*
	 * Mark this delta has a having a super type change
	 */
	default void superTypes() {
		changed(IJavaElementDelta.F_SUPER_TYPES);
	}
}
