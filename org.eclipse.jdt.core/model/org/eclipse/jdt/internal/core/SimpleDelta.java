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

import org.eclipse.jdt.core.IJavaElementDelta;

/**
 * A simple Java element delta that remembers the kind of changes only.
 */
public class SimpleDelta implements ISimpleDelta {

	/*
	 * @see IJavaElementDelta#getKind()
	 */
	protected int kind = 0;

	/*
	 * @see IJavaElementDelta#getFlags()
	 */
	protected int changeFlags = 0;

	/*
	 * Marks this delta as added
	 */
	public void added() {
		this.kind = IJavaElementDelta.ADDED;
	}

	/*
	 * Marks this delta as changed with the given change flag
	 */
	public void changed(int flags) {
		this.kind = IJavaElementDelta.CHANGED;
		this.changeFlags |= flags;
	}

	/*
	 * @see IJavaElementDelta#getFlags()
	 */
	public int getFlags() {
		return this.changeFlags;
	}

	/*
	 * @see IJavaElementDelta#getKind()
	 */
	public int getKind() {
		return this.kind;
	}

	/*
	 * Marks this delta as removed
	 */
	public void removed() {
		this.kind = IJavaElementDelta.REMOVED;
		this.changeFlags = 0;
	}

	protected void toDebugString(StringBuffer buffer) {
		buffer.append("["); //$NON-NLS-1$
		switch (getKind()) {
			case IJavaElementDelta.ADDED :
				buffer.append('+');
				break;
			case IJavaElementDelta.REMOVED :
				buffer.append('-');
				break;
			case IJavaElementDelta.CHANGED :
				buffer.append('*');
				break;
			default :
				buffer.append('?');
				break;
		}
		buffer.append("]: {"); //$NON-NLS-1$
		toDebugString(buffer, getFlags());
		buffer.append("}"); //$NON-NLS-1$
	}

	protected boolean toDebugString(StringBuffer buffer, int flags) {
		boolean prev = false;
		if ((flags & IJavaElementDelta.F_MODIFIERS) != 0) {
			if (prev)
				buffer.append(" | "); //$NON-NLS-1$
			buffer.append("MODIFIERS CHANGED"); //$NON-NLS-1$
			prev = true;
		}
		if ((flags & IJavaElementDelta.F_SUPER_TYPES) != 0) {
			if (prev)
				buffer.append(" | "); //$NON-NLS-1$
			buffer.append("SUPER TYPES CHANGED"); //$NON-NLS-1$
			prev = true;
		}
		return prev;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		toDebugString(buffer);
		return buffer.toString();
	}
}
