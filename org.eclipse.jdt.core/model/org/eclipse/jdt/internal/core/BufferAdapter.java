/*******************************************************************************
 * Copyright (c) 2017, 2018 1C-Soft LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vladimir Piskarev (1C) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.handly.buffer.BufferChangeOperation;
import org.eclipse.handly.buffer.IBufferChange;
import org.eclipse.handly.context.IContext;
import org.eclipse.handly.snapshot.DocumentSnapshot;
import org.eclipse.handly.snapshot.ISnapshot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;

class BufferAdapter implements org.eclipse.handly.buffer.IBuffer {
	
	private final org.eclipse.jdt.core.IBuffer buffer;
	private final IDocument document;
	
	public BufferAdapter(org.eclipse.jdt.core.IBuffer buffer) {
		this.buffer = buffer;
		this.document = new DocumentAdapter(buffer);
	}

	public org.eclipse.jdt.core.IBuffer getAdaptedBuffer() {
		return this.buffer;
	}

	@Override
	public ISnapshot getSnapshot() {
		return new DocumentSnapshot(this.document);
	}

	@Override
	public IBufferChange applyChange(IBufferChange change, IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor = new NullProgressMonitor();
		try {
			BufferChangeOperation operation = new BufferChangeOperation(this, change);
			return operation.execute(monitor);
		}
		catch (MalformedTreeException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaCore.PLUGIN_ID, e.getMessage(), e));
		}
		catch (BadLocationException e) {
			throw new CoreException(new Status(IStatus.ERROR, JavaCore.PLUGIN_ID, e.getMessage(), e));
		}
	}

	@Override
	public void save(IContext context, IProgressMonitor monitor) throws CoreException {
		this.buffer.save(monitor, false);
	}

	@Override
	public boolean isDirty() {
		return this.buffer.hasUnsavedChanges();
	}

	@Override
	public IDocument getDocument() {
		return this.document;
	}

	@Override
	public void addRef() {
		// do nothing
	}

	@Override
	public void release() {
		// do nothing
	}
}
