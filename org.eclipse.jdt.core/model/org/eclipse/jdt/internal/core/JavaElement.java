/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import static org.eclipse.handly.context.Contexts.EMPTY_CONTEXT;
import static org.eclipse.handly.context.Contexts.of;
import static org.eclipse.handly.context.Contexts.with;
import static org.eclipse.handly.util.ToStringOptions.FORMAT_STYLE;
import static org.eclipse.handly.util.ToStringOptions.FormatStyle.MEDIUM;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.handly.context.Contexts;
import org.eclipse.handly.context.IContext;
import org.eclipse.handly.model.IElement;
import org.eclipse.handly.model.impl.support.IElementImplSupport;
import org.eclipse.handly.model.impl.support.IModelManager;
import org.eclipse.handly.util.Property;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.core.util.MementoTokenizer;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * Root of Java element handle hierarchy.
 *
 * @see IJavaElement
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class JavaElement extends PlatformObject implements IJavaElement, IElementImplSupport {
//	private static final QualifiedName PROJECT_JAVADOC= new QualifiedName(JavaCore.PLUGIN_ID, "project_javadoc_location"); //$NON-NLS-1$
	/**
	 * Shared empty collection used for efficiency.
	 */
	private static final byte[] CLOSING_DOUBLE_QUOTE = new byte[] { 34 };
	/* To handle the pre - HTML 5 format: <META http-equiv="Content-Type" content="text/html; charset=UTF-8">  */
	private static final byte[] CHARSET = new byte[] { 99, 104, 97, 114, 115, 101, 116, 61 };
	/* To handle the HTML 5 format: <meta http-equiv="Content-Type" content="text/html" charset="UTF-8"> */
	private static final byte[] CHARSET_HTML5 = new byte[] { 99, 104, 97, 114, 115, 101, 116, 61, 34 };
	private static final byte[] META_START = new byte[] { 60, 109, 101, 116, 97 };
	private static final byte[] META_END = new byte[] { 34, 62 };
	public static final char JEM_ESCAPE = '\\';
	public static final char JEM_JAVAPROJECT = '=';
	public static final char JEM_PACKAGEFRAGMENTROOT = '/';
	public static final char JEM_PACKAGEFRAGMENT = '<';
	public static final char JEM_FIELD = '^';
	public static final char JEM_METHOD = '~';
	public static final char JEM_INITIALIZER = '|';
	public static final char JEM_COMPILATIONUNIT = '{';
	public static final char JEM_CLASSFILE = '(';
	public static final char JEM_TYPE = '[';
	public static final char JEM_PACKAGEDECLARATION = '%';
	public static final char JEM_IMPORTDECLARATION = '#';
	public static final char JEM_COUNT = '!';
	public static final char JEM_LOCALVARIABLE = '@';
	public static final char JEM_TYPE_PARAMETER = ']';
	public static final char JEM_ANNOTATION = '}';
	public static final char JEM_LAMBDA_EXPRESSION = ')';
	public static final char JEM_LAMBDA_METHOD = '&';
	public static final char JEM_STRING = '"';
	
	/**
	 * Before ')', '&' and '"' became the newest additions as delimiters, the former two
	 * were allowed as part of element attributes and possibly stored. Trying to recreate 
	 * elements from such memento would cause undesirable results. Consider the following 
	 * valid project name: (abc)
	 * If we were to use ')' alone as the delimiter and decode the above name, the memento
	 * would be wrongly identified to contain a lambda expression.  
	 *
	 * In order to differentiate delimiters from characters that are part of element attributes, 
	 * the following escape character is being introduced and all the new delimiters must 
	 * be escaped with this. So, a lambda expression would be written as: "=)..."
	 * 
	 * @see JavaElement#appendEscapedDelimiter(StringBuffer, char)
	 */
	public static final char JEM_DELIMITER_ESCAPE = JEM_JAVAPROJECT;
	
	public static final Property<Boolean> SHOW_RESOLVED_INFO = Property.get(JavaElement.class.getName() + ".showResolvedInfo", Boolean.class).withDefault(false); //$NON-NLS-1$

	protected static final JavaElement[] NO_ELEMENTS = new JavaElement[0];

	private static Set<String> invalidURLs = null;
	
	private static Set<String> validURLs = null;
	protected static URL getLibraryJavadocLocation(IClasspathEntry entry) throws JavaModelException {
		switch(entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY :
			case IClasspathEntry.CPE_VARIABLE :
				break;
			default :
				throw new IllegalArgumentException("Entry must be of kind CPE_LIBRARY or CPE_VARIABLE"); //$NON-NLS-1$
		}

		IClasspathAttribute[] extraAttributes= entry.getExtraAttributes();
		for (int i= 0; i < extraAttributes.length; i++) {
			IClasspathAttribute attrib= extraAttributes[i];
			if (IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME.equals(attrib.getName())) {
				String value = attrib.getValue();
				try {
					return new URL(value);
				} catch (MalformedURLException e) {
					throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC, value));
				}
			}
		}
		return null;
	}

	/**
	 * This element's parent, or <code>null</code> if this
	 * element does not have a parent.
	 */
	protected JavaElement parent;
	/**
	 * Constructs a handle for a java element with
	 * the given parent element.
	 *
	 * @param parent The parent of java element
	 *
	 * @exception IllegalArgumentException if the type is not one of the valid
	 *		Java element type constants
	 *
	 */
	protected JavaElement(JavaElement parent) throws IllegalArgumentException {
		this.parent = parent;
	}
	/**
	 * @see #JEM_DELIMITER_ESCAPE
	 */
	protected void appendEscapedDelimiter(StringBuffer buffer, char delimiter) {
		buffer.append(JEM_DELIMITER_ESCAPE);
		buffer.append(delimiter);
	}
	@Override
	public boolean canEqual_(Object obj) {
		if (!(obj instanceof JavaElement)) return false;
		return getElementType() == ((JavaElement) obj).getElementType();
	}
	/**
	 * @see IOpenable
	 */
	public final void close() {
		close_();
	}
	/**
	 * Returns true if this handle represents the same Java element
	 * as the given handle. By default, two handles represent the same
	 * element if they are identical or if they represent the same type
	 * of element, have equal names, parents, and occurrence counts.
	 *
	 * <p>If a subclass has other requirements for equality, this method
	 * must be overridden.
	 *
	 * @see Object#equals
	 */
	public boolean equals(Object o) {

		if (this == o) return true;

		// Java model parent is null
		if (this.parent == null) return super.equals(o);

		if (!(o instanceof JavaElement))
			return false;
		JavaElement other = (JavaElement) o;
		if (!other.canEqual_(this))
			return false;
		return getElementName().equals(other.getElementName()) &&
				this.parent.equals(other.parent);
	}
	/*
	 * Do not add new delimiters here
	 */
	protected void escapeMementoName(StringBuffer buffer, String mementoName) {
		for (int i = 0, length = mementoName.length(); i < length; i++) {
			char character = mementoName.charAt(i);
			switch (character) {
				case JEM_ESCAPE:
				case JEM_COUNT:
				case JEM_JAVAPROJECT:
				case JEM_PACKAGEFRAGMENTROOT:
				case JEM_PACKAGEFRAGMENT:
				case JEM_FIELD:
				case JEM_METHOD:
				case JEM_INITIALIZER:
				case JEM_COMPILATIONUNIT:
				case JEM_CLASSFILE:
				case JEM_TYPE:
				case JEM_PACKAGEDECLARATION:
				case JEM_IMPORTDECLARATION:
				case JEM_LOCALVARIABLE:
				case JEM_TYPE_PARAMETER:
				case JEM_ANNOTATION:
					buffer.append(JEM_ESCAPE);
			}
			buffer.append(character);
		}
	}
	/**
	 * @see IJavaElement
	 */
	public final boolean exists() {
		return exists_();
	}
	/**
	 * Returns the <code>ASTNode</code> that corresponds to this <code>JavaElement</code>
	 * or <code>null</code> if there is no corresponding node.
	 */
	public ASTNode findNode(CompilationUnit ast) {
		return null; // works only inside a compilation unit
	}
	/**
	 * @see IJavaElement
	 */
	public IJavaElement getAncestor(int ancestorType) {

		IJavaElement element = this;
		while (element != null) {
			if (element.getElementType() == ancestorType)  return element;
			element= element.getParent();
		}
		return null;
	}
	/*
	 * @see IJavaElement#getAttachedJavadoc(IProgressMonitor)
	 */
	public String getAttachedJavadoc(IProgressMonitor monitor) throws JavaModelException {
		return null;
	}
	/**
	 * @see IParent
	 */
	public final IJavaElement[] getChildren() throws JavaModelException {
		try {
			return getChildren_(EMPTY_CONTEXT, null);
		} catch (CoreException e) {
			throw Util.toJavaModelException(e);
		}
	}
	@Override
	public JavaElement[] getChildren_(IContext context, IProgressMonitor monitor) throws CoreException {
		return getChildrenFromBody_(getBody_(context, monitor));
	}
	@Override
	public JavaElement[] getChildrenFromBody_(Object body) {
		if (body instanceof JavaElementInfo) {
			return ((JavaElementInfo) body).getChildren();
		}
		else {
			return NO_ELEMENTS;
		}
	}
	/**
	 * Returns a collection of (immediate) children of this node of the
	 * specified type.
	 *
	 * @param type - one of the JEM_* constants defined by JavaElement
	 */
	public ArrayList getChildrenOfType(int type) throws JavaModelException {
		IJavaElement[] children = getChildren();
		int size = children.length;
		ArrayList list = new ArrayList(size);
		for (int i = 0; i < size; ++i) {
			IJavaElement elt = children[i];
			if (elt.getElementType() == type) {
				list.add(elt);
			}
		}
		return list;
	}
	/**
	 * @see IMember
	 */
	public IClassFile getClassFile() {
		return null;
	}

	/**
	 * @see IMember
	 */
	public ICompilationUnit getCompilationUnit() {
		return null;
	}

	/**
	 * Returns the info for this handle.
	 * If this element is not already open, it and all of its parents are opened.
	 * Does not return null.
	 * NOTE: BinaryType infos are NOT rooted under JavaElementInfo.
	 * @exception JavaModelException if the element is not present or not accessible
	 */
	public final Object getElementInfo() throws JavaModelException {
		return getElementInfo(null);
	}
	/**
	 * Returns the info for this handle.
	 * If this element is not already open, it and all of its parents are opened.
	 * Does not return null.
	 * NOTE: BinaryType infos are NOT rooted under JavaElementInfo.
	 * @exception JavaModelException if the element is not present or not accessible
	 */
	public final Object getElementInfo(IProgressMonitor monitor) throws JavaModelException {
		try {
			return getBody_(EMPTY_CONTEXT, monitor);
		} catch (CoreException e) {
			throw Util.toJavaModelException(e);
		}
	}
	/**
	 * @see IAdaptable
	 */
	public String getElementName() {
		return ""; //$NON-NLS-1$
	}
	/*
	 * Creates a Java element handle from the given memento.
	 * The given working copy owner is used only for compilation unit handles.
	 */
	public IJavaElement getHandleFromMemento(MementoTokenizer memento, WorkingCopyOwner owner) {
		if (!memento.hasMoreTokens()) return this;
		String token = memento.nextToken();
		return getHandleFromMemento(token, memento, owner);
	}
	/*
	 * Creates a Java element handle from the given memento.
	 * The given token is the current delimiter indicating the type of the next token(s).
	 * The given working copy owner is used only for compilation unit handles.
	 */
	public abstract IJavaElement getHandleFromMemento(String token, MementoTokenizer memento, WorkingCopyOwner owner);
	/**
	 * @see IJavaElement
	 */
	public String getHandleIdentifier() {
		return getHandleMemento();
	}
	/**
	 * @see JavaElement#getHandleMemento()
	 */
	public String getHandleMemento(){
		StringBuffer buff = new StringBuffer();
		getHandleMemento(buff);
		return buff.toString();
	}
	protected void getHandleMemento(StringBuffer buff) {
		((JavaElement)getParent()).getHandleMemento(buff);
		buff.append(getHandleMementoDelimiter());
		escapeMementoName(buff, getElementName());
	}
	/**
	 * Returns the <code>char</code> that marks the start of this handles
	 * contribution to a memento.
	 */
	protected abstract char getHandleMementoDelimiter();
	int getIndexOf(byte[] array, byte[] toBeFound, int start, int end) {
		if (array == null || toBeFound == null)
			return -1;
		final int toBeFoundLength = toBeFound.length;
		final int arrayLength = (end != -1 && end < array.length) ? end : array.length;
		if (arrayLength < toBeFoundLength)
			return -1;
		loop: for (int i = start, max = arrayLength - toBeFoundLength + 1; i < max; i++) {
			if (isSameCharacter(array[i], toBeFound[0])) {
				for (int j = 1; j < toBeFoundLength; j++) {
					if (!isSameCharacter(array[i + j], toBeFound[j]))
						continue loop;
				}
				return i;
			}
		}
		return -1;
	}
	protected URL getJavadocBaseLocation() throws JavaModelException {
		IPackageFragmentRoot root= (IPackageFragmentRoot) getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (root == null) {
			return null;
		}

		if (root.getKind() == IPackageFragmentRoot.K_BINARY) {
			IClasspathEntry entry= null;
			try {
				entry= root.getResolvedClasspathEntry();
				URL url = getLibraryJavadocLocation(entry);
				if (url != null) {
					return url;
				}
			}
			catch(JavaModelException jme) {
				// Proceed with raw classpath
			}
			
			entry= root.getRawClasspathEntry();
			switch (entry.getEntryKind()) {
				case IClasspathEntry.CPE_LIBRARY:
				case IClasspathEntry.CPE_VARIABLE:
					return getLibraryJavadocLocation(entry);
				default:
					return null;
			}			
		}
		return null;
	}
	/**
	 * @see IJavaElement
	 */
	public IJavaModel getJavaModel() {
		IJavaElement current = this;
		do {
			if (current instanceof IJavaModel) return (IJavaModel) current;
		} while ((current = current.getParent()) != null);
		return null;
	}
	public JavaModelManager getJavaModelManager() {
		return JavaModelManager.getJavaModelManager();
	}
	/**
	 * @see IJavaElement
	 */
	public IJavaProject getJavaProject() {
		IJavaElement current = this;
		do {
			if (current instanceof IJavaProject) return (IJavaProject) current;
		} while ((current = current.getParent()) != null);
		return null;
	}
	@Override
	public final IModelManager getModelManager_() {
		return getJavaModelManager();
	}
	@Override
	public final String getName_() {
		return getElementName();
	}
	/*
	 * @see IJavaElement
	 */
	public IOpenable getOpenable() {
		return getOpenableParent();
	}
	/**
	 * Return the first instance of IOpenable in the parent
	 * hierarchy of this element.
	 *
	 * <p>Subclasses that are not IOpenable's must override this method.
	 */
	public IOpenable getOpenableParent() {
		return (IOpenable)this.parent;
	}
	/**
	 * @see IJavaElement
	 */
	public final IJavaElement getParent() {
		return this.parent;
	}
	@Override
	public final IElement getParent_() {
		return this.parent;
	}
	/*
	 * @see IJavaElement#getPrimaryElement()
	 */
	public IJavaElement getPrimaryElement() {
		return getPrimaryElement(true);
	}
	/*
	 * Returns the primary element. If checkOwner, and the cu owner is primary,
	 * return this element.
	 */
	public IJavaElement getPrimaryElement(boolean checkOwner) {
		return this;
	}
	public IResource getResource() {
		return resource();
	}
	@Override
	public final IResource getResource_() {
		return getResource();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.core.IJavaElement#getSchedulingRule()
	 */
	public ISchedulingRule getSchedulingRule() {
		IResource resource = resource();
		if (resource == null) {
			class NoResourceSchedulingRule implements ISchedulingRule {
				public IPath path;
				public NoResourceSchedulingRule(IPath path) {
					this.path = path;
				}
				public boolean contains(ISchedulingRule rule) {
					if (rule instanceof NoResourceSchedulingRule) {
						return this.path.isPrefixOf(((NoResourceSchedulingRule)rule).path);
					} else {
						return false;
					}
				}
				public boolean isConflicting(ISchedulingRule rule) {
					if (rule instanceof NoResourceSchedulingRule) {
						IPath otherPath = ((NoResourceSchedulingRule)rule).path;
						return this.path.isPrefixOf(otherPath) || otherPath.isPrefixOf(this.path);
					} else {
						return false;
					}
				}
			}
			return new NoResourceSchedulingRule(getPath());
		} else {
			return resource;
		}
	}
	/**
	 * Returns the element that is located at the given source position
	 * in this element.  This is a helper method for <code>ICompilationUnit#getElementAt</code>,
	 * and only works on compilation units and types. The position given is
	 * known to be within this element's source range already, and if no finer
	 * grained element is found at the position, this element is returned.
	 */
	protected IJavaElement getSourceElementAt(int position) throws JavaModelException {
		if (this instanceof ISourceReference) {
			IJavaElement[] children = getChildren();
			for (int i = children.length-1; i >= 0; i--) {
				IJavaElement aChild = children[i];
				if (aChild instanceof SourceRefElement) {
					SourceRefElement child = (SourceRefElement) children[i];
					ISourceRange range = child.getSourceRange();
					int start = range.getOffset();
					int end = start + range.getLength();
					if (start <= position && position <= end) {
						if (child instanceof IField) {
							// check muti-declaration case (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=39943)
							int declarationStart = start;
							SourceRefElement candidate = null;
							do {
								// check name range
								range = ((IField)child).getNameRange();
								if (position <= range.getOffset() + range.getLength()) {
									candidate = child;
								} else {
									return candidate == null ? child.getSourceElementAt(position) : candidate.getSourceElementAt(position);
								}
								child = --i>=0 ? (SourceRefElement) children[i] : null;
							} while (child != null && child.getSourceRange().getOffset() == declarationStart);
							// position in field's type: use first field
							return candidate.getSourceElementAt(position);
						} else if (child instanceof IParent) {
							return child.getSourceElementAt(position);
						} else {
							return child;
						}
					}
				}
			}
		} else {
			// should not happen
			Assert.isTrue(false);
		}
		return this;
	}
	/**
	 * Returns the SourceMapper facility for this element, or
	 * <code>null</code> if this element does not have a
	 * SourceMapper.
	 */
	public SourceMapper getSourceMapper() {
		return ((JavaElement)getParent()).getSourceMapper();
	}
	protected String getURLContents(URL baseLoc, String docUrlValue) throws JavaModelException {
		InputStream stream = null;
		JarURLConnection connection2 = null;
		URL docUrl = null;
		URLConnection connection = null;
		try {
			redirect: for (int i= 0; i < 5; i++) { // avoid endless redirects...
				docUrl = new URL(docUrlValue);
				connection = docUrl.openConnection();

				int timeoutVal = 10000;
				connection.setConnectTimeout(timeoutVal);
				connection.setReadTimeout(timeoutVal);

				if (connection instanceof HttpURLConnection) {
					// HttpURLConnection doesn't redirect from http to https, see https://bugs.eclipse.org/450684
					HttpURLConnection httpCon = (HttpURLConnection) connection;
					if (httpCon.getResponseCode() == 301) {
						docUrlValue = httpCon.getHeaderField("location"); //$NON-NLS-1$
						if (docUrlValue != null) {
							continue redirect;
						}
					}
				} else if (connection instanceof JarURLConnection) {
					connection2 = (JarURLConnection) connection;
					// https://bugs.eclipse.org/bugs/show_bug.cgi?id=156307
					connection.setUseCaches(false);
				}
				break;
			}

			stream = new BufferedInputStream(connection.getInputStream());

			String encoding = connection.getContentEncoding();
			byte[] contents = org.eclipse.jdt.internal.compiler.util.Util.getInputStreamAsByteArray(stream, connection.getContentLength());
			if (encoding == null) {
				int index = getIndexOf(contents, META_START, 0, -1);
				if (index != -1) {
					int end = getIndexOf(contents, META_END, index, -1);
					if (end != -1) {
						if ((end + 1) <= contents.length) end++;
						int charsetIndex = getIndexOf(contents, CHARSET_HTML5, index, end);
						if (charsetIndex == -1) {
							charsetIndex = getIndexOf(contents, CHARSET, index, end);
							if (charsetIndex != -1)
								charsetIndex = charsetIndex + CHARSET.length;
						} else {
							charsetIndex = charsetIndex + CHARSET_HTML5.length;
						}
						if (charsetIndex != -1) {
							end = getIndexOf(contents, CLOSING_DOUBLE_QUOTE, charsetIndex, end);
							encoding = new String(contents, charsetIndex, end - charsetIndex, org.eclipse.jdt.internal.compiler.util.Util.UTF_8);
						}
					}
				}
			}
			try {
				if (encoding == null) {
					encoding = getJavaProject().getProject().getDefaultCharset();
				}
			} catch (CoreException e) {
				// ignore
			}
			if (contents != null) {
				if (encoding != null) {
					return new String(contents, encoding);
				} else {
					// platform encoding is used
					return new String(contents);
				}
			}
		} catch (IllegalArgumentException e) {
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=304316
			return null;
		} catch (NullPointerException e) {
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=304316
			return null;
		} catch (SocketTimeoutException e) {
			throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC_TIMEOUT, this));
		} catch (MalformedURLException e) {
			throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC, this));
		} catch (FileNotFoundException e) {
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=403154
			validateAndCache(baseLoc, e);
		} catch (SocketException e) {
			// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=247845 &
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=400060
			throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);
		} catch (UnknownHostException e) {
			// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=247845 &
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=400060
			throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);
		} catch (ProtocolException e) {
			// see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=247845 &
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=400060
			throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);
		} catch (IOException e) {
			throw new JavaModelException(e, IJavaModelStatusConstants.IO_EXCEPTION);
		} catch(Exception e) {
			if (e.getCause() instanceof IllegalArgumentException) return null;
			throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if (connection2 != null) {
				try {
					connection2.getJarFile().close();
				} catch(IOException e) {
					// ignore
				} catch(IllegalStateException e) {
					/*
					 * ignore. Can happen in case the stream.close() did close the jar file
					 * see https://bugs.eclipse.org/bugs/show_bug.cgi?id=140750
					 */
				}
 			}
		}
		return null;
	}

	/**
	 * @see IParent
	 */
	public boolean hasChildren() throws JavaModelException {
		// if I am not open, return true to avoid opening (case of a Java project, a compilation unit or a class file).
		// also see https://bugs.eclipse.org/bugs/show_bug.cgi?id=52474
		Object body = findBody_();
		if (body instanceof JavaElementInfo) {
			return ((JavaElementInfo)body).getChildren().length > 0;
		} else {
			return true;
		}
	}
	/**
	 * Returns the hash code for this Java element. By default,
	 * the hash code for an element is a combination of its name
	 * and parent's hash code. Elements with other requirements must
	 * override this method.
	 */
	public int hashCode() {
		if (this.parent == null) return super.hashCode();
		return Util.combineHashCodes(getElementName().hashCode(), this.parent.hashCode());
	}

	/**
	 * Returns true if this element is an ancestor of the given element,
	 * otherwise false.
	 */
	public boolean isAncestorOf(IJavaElement e) {
		IJavaElement parentElement= e.getParent();
		while (parentElement != null && !parentElement.equals(this)) {
			parentElement= parentElement.getParent();
		}
		return parentElement != null;
	}
	/**
	 * @see IJavaElement
	 */
	public boolean isReadOnly() {
		return false;
	}
	boolean isSameCharacter(byte b1, byte b2) {
		if (b1 == b2 || Character.toUpperCase((char) b1) == Character.toUpperCase((char) b2)) {
			return true;
		}
		return false;
	}
	@Override
	public final CoreException newDoesNotExistException_() {
		return newNotPresentException();
	}
	protected JavaModelStatus newDoesNotExistStatus() {
		return new JavaModelStatus(IJavaModelStatusConstants.ELEMENT_DOES_NOT_EXIST, this);
	}
	/**
	 * Creates and returns a new Java model exception for this element with the given status.
	 */
	public JavaModelException newJavaModelException(IStatus status) {
		if (status instanceof IJavaModelStatus)
			return new JavaModelException((IJavaModelStatus) status);
		else
			return new JavaModelException(new JavaModelStatus(status.getSeverity(), status.getCode(), status.getMessage()));
	}
	/**
	 * Creates and returns a new not present exception for this element.
	 */
	public JavaModelException newNotPresentException() {
		return new JavaModelException(newDoesNotExistStatus());
	}
	@Override
	public void openParent_(IContext context, IProgressMonitor monitor) throws CoreException {
		Openable openableParent = (Openable) getOpenableParent();
		if (openableParent != null && openableParent.findBody_() == null)
			openableParent.open_(context, monitor);
	}
	/**
	 */
	public String readableName() {
		return getElementName();
	}
//	@Override
//	public void remove_(IContext context) {
//		synchronized (getElementManager_()) {
//			Object body = peekAtBody_();
//			if (body != null) {
//				boolean wasVerbose = false;
//				try {
//					if (JavaModelCache.VERBOSE) {
//						String elementType;
//						switch (getElementType()) {
//							case IJavaElement.JAVA_PROJECT:
//								elementType = "project"; //$NON-NLS-1$
//								break;
//							case IJavaElement.PACKAGE_FRAGMENT_ROOT:
//								elementType = "root"; //$NON-NLS-1$
//								break;
//							case IJavaElement.PACKAGE_FRAGMENT:
//								elementType = "package"; //$NON-NLS-1$
//								break;
//							case IJavaElement.CLASS_FILE:
//								elementType = "class file"; //$NON-NLS-1$
//								break;
//							case IJavaElement.COMPILATION_UNIT:
//								elementType = "compilation unit"; //$NON-NLS-1$
//								break;
//							default:
//								elementType = "element"; //$NON-NLS-1$
//						}
//						System.out.println(
//								Thread.currentThread() + " CLOSING " + elementType + " " + toStringWithAncestors()); //$NON-NLS-1$//$NON-NLS-2$
//						wasVerbose = true;
//						JavaModelCache.VERBOSE = false;
//					}
//					IElementImplSupport.super.remove_(context);
//					if (wasVerbose) {
//						System.out.println(getJavaModelManager().getElementManager().cacheToString("-> ")); //$NON-NLS-1$
//					}
//				} finally {
//					JavaModelCache.VERBOSE = wasVerbose;
//				}
//			}
//		}
//	}
	public JavaElement resolved(Binding binding) {
		return this;
	}
	public abstract IResource resource();

	/**
	 * Debugging purposes
	 */
	public final String toDebugString() {
		StringBuilder builder = new StringBuilder();
		toStringBody_(builder, NO_BODY, Contexts.EMPTY_CONTEXT);
		return builder.toString();
	}

	/**
	 *  Debugging purposes
	 */
	public final String toString() {
		return toString_(EMPTY_CONTEXT);
	}

	@Override
	public void toStringAncestors_(StringBuilder builder, IContext context) {
		IElementImplSupport.super.toStringAncestors_(builder,
			with(of(SHOW_RESOLVED_INFO, false), context));
	}

	/**
	 *  Debugging purposes
	 */
	public final String toStringWithAncestors() {
		return toStringWithAncestors(true/*show resolved info*/);
	}
	/**
	 *  Debugging purposes
	 */
	public final String toStringWithAncestors(boolean showResolvedInfo) {
		return toString_(with(of(FORMAT_STYLE, MEDIUM), of(SHOW_RESOLVED_INFO, showResolvedInfo)));
	}
	
	public JavaElement unresolved() {
		return this;
	}

	/*
	 * This method caches a list of good and bad Javadoc locations in the current eclipse session. 
	 */
	protected void validateAndCache(URL baseLoc, FileNotFoundException e) throws JavaModelException {
		String url = baseLoc.toString();
		if (validURLs != null && validURLs.contains(url)) return;
		
		if (invalidURLs != null && invalidURLs.contains(url)) 
				throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);

		InputStream input = null;
		try {
			URLConnection connection = baseLoc.openConnection();
			input = connection.getInputStream();
			if (validURLs == null) {
				validURLs = new HashSet<String>(1);
			}
			validURLs.add(url);
		} catch (Exception e1) {
			if (invalidURLs == null) { 
				invalidURLs = new HashSet<String>(1);
			}
			invalidURLs.add(url);
			throw new JavaModelException(e, IJavaModelStatusConstants.CANNOT_RETRIEVE_ATTACHED_JAVADOC);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Exception e1) {
					// Ignore
				}
			}
		}
	}
}
