/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.model;

import java.util.HashMap;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaProject;

import junit.framework.*;

/**
 * DO NOT RELEASE AS PART OF REGRESSION TEST - if failing, can cause testing hang
 */
public class ThreadSafetyTests extends ModifyingResourceTests {

	public class Semaphore {
		
		private String name = null;
		private int permissions = 0;
		
		public Semaphore(int initialPermisions) {
			this(null, initialPermisions);
		}
		public Semaphore(String name, int initialPermissions) {
			this.name = name;
			this.permissions = initialPermissions;
		}
		public synchronized void acquire() {
			while (this.permissions <= 0){
				try {
					if (this.name != null) System.out.println(Thread.currentThread() + " - waiting to acquire: " + this.name); //$NON-NLS-1$
					wait();
				} catch(InterruptedException e){
				}
			}
			this.permissions--;
			if (this.name != null) System.out.println(Thread.currentThread() + " - acquired: " + this.name); //$NON-NLS-1$
		}
		public synchronized void release() {
			if (this.name != null) System.out.println(Thread.currentThread() + " - releasing: " + this.name); //$NON-NLS-1$
			if (++this.permissions > 0) notifyAll();
		}
	}

public ThreadSafetyTests(String name) {
	super(name);
}

public static Test suite() {

	if (false){
		TestSuite suite = new Suite(ThreadSafetyTests.class.getName());
		suite.addTest(new ClasspathTests("testDeadlock01"));
		return suite;
	}
	return new Suite(ThreadSafetyTests.class);	
}
/**
 * 33231 - deadlocked if activating initializer while some concurrent action is populating the JavaModel
 */
public void testDeadlock01() throws CoreException {

	System.out.println("Test deadlock scenario");
	try {
		final IJavaProject project = this.createJavaProject(
				"P", 
				new String[] {}, 
				new String[] {"org.eclipse.jdt.core.tests.model.TEST_CONTAINER"}, 
				"");
		
		// simulate state on startup (flush containers, and discard their previous values)
		project.getJavaModel().close();
		JavaModelManager.PreviousSessionContainers = new HashMap(5);
		JavaModelManager.Containers = new HashMap(5);
		JavaModelManager.getJavaModelManager().removePerProjectInfo((JavaProject)project);

		// use a thread to hold the lock, so as to recreate potential deadlock situation
		final Semaphore step1 = new Semaphore("<1:permission to populate JavaModel inducing containers inits>", 0); // first acquisition will wait
		final Semaphore step2 = new Semaphore("<2:permission to perform resource modification >", 0); // first acquisition to wait
		final Semaphore hasCompleted = new Semaphore(0); 
		
		ContainerInitializer.setInitializer(new ClasspathInitializerTests.DefaultContainerInitializer(new String[] {"P", ""}){
			public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
				step2.release();
				System.out.println(Thread.currentThread() + " initializer has started: attempting to acquire workspace lock");
				super.initialize(containerPath, project);
				System.out.println(Thread.currentThread() + " initializer has finished");
			}
		});

		// trigger some delta notification in different thread
		Thread performJavaOperationInsideWorkspaceLock = new Thread(new Runnable(){
				public void run() {
					try {
						ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
							public void run(IProgressMonitor monitor)	throws CoreException {
								System.out.println(Thread.currentThread() + " got workspace lock");
								step1.release();
								step2.acquire(); // ensure the java model lock is taken
								ThreadSafetyTests.this.createFile("/P/X.java", "public class X {}");
								System.out.println(Thread.currentThread() + " created file X.java");
							}
						}, null);
					} catch (CoreException e) {
					}
					hasCompleted.release();
					System.out.println(Thread.currentThread() +" ResourceModification DONE");
				}
			},"ModifyResource");
		performJavaOperationInsideWorkspaceLock.setDaemon(true);
		performJavaOperationInsideWorkspaceLock.start();

		Thread attemptPopulateTheJavaModel = new Thread(new Runnable(){
				public void run() {
					try {
							step1.acquire(); // ensure workspace lock is taken already
							System.out.println(Thread.currentThread() + " about to populate Java model");
							// needs the JavaModel lock to populate the project
							project.getChildren(); // trigger classpath initializer activation (requires workspace lock)
							System.out.println(Thread.currentThread() + " done populating the model");
					} catch (JavaModelException e) {
					}
					hasCompleted.release();
					System.out.println(Thread.currentThread() +" Populate JavaModel DONE");
				}
			},"PopulateModel");
		attemptPopulateTheJavaModel.setDaemon(true);
		attemptPopulateTheJavaModel.start();

		hasCompleted.acquire(); // ensure both actions did complete
		hasCompleted.acquire();
		System.out.println("SUCCESS - no deadlock encountered");
	} finally {
		// cleanup  
		this.deleteProject("P");
	}
}
}
