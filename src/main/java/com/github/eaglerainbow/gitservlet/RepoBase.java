package com.github.eaglerainbow.gitservlet;
/*
 * Copyright 2015 Nico Schmoigl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;

public class RepoBase {
	private final HashMap<String, File> repoPaths;
	private File baseDir;
	
	/* for concept, see also https://docs.oracle.com/javase/tutorial/essential/io/notification.html */
	private WatchService watcher;
	private WatchKey watchKey;
	private WatchThread watchThread;
	
	public RepoBase(File baseDir) {
		this.repoPaths = new HashMap<String, File>();
		this.baseDir = baseDir;

		if (baseDir == null) {
			throw new Error("No Base Dir provided");
		}
		
		this.initialize();
		
		try {
			this.watcher = FileSystems.getDefault().newWatchService();
			this.watchKey = this.baseDir.toPath().register(this.watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			
			this.watchThread = new WatchThread();
			this.watchThread.start();
		} catch (IOException e) {
			this.watcher = null;
			System.err.println("Unable to register file change notification service!");
			e.printStackTrace(System.err);
		}
	}

	private class WatchThread extends Thread {
		public WatchThread() {
			super("WatchThread BaseDirectory "+baseDir.toString());
		}
		
		@Override
		public void run() {
			for (;;) {
				WatchKey key = null;
				try {
					key = watcher.take();
				} catch (InterruptedException e) {
					return; // stop execution
				}
				
				if (key == null) {
					// nothing there to process
					continue;
				}
				
				for (WatchEvent<?> event : key.pollEvents()) {
					Kind<?> kind = event.kind();
					
					if (kind == OVERFLOW) {
						// Attempt to reinitialize
						repoPaths.clear();
						initialize();
						// TODO does this work this way?
						break;
					}
					
					WatchEvent<Path> evp = (WatchEvent<Path>) event;
					
					// Context for directory entry event is the file name of entry
					Path name = evp.context(); // that subdir has triggered the change
					Path fullpath = baseDir.toPath().resolve(name.toString());
					
					System.err.format("%s: %s\n", evp.kind().name(), fullpath.toString());
					
					if (kind == ENTRY_CREATE) {
						// new directory has been added
						repoPaths.put(name.toFile().getName(), fullpath.toFile());
						/*
						 * Note that although this is in a concurrent thread situation,
						 * this thread is the only thread which is allowed to perform
						 * changes to the HashMap. All other threads are just reading
						 * it and may experience a "dirty read". 
						 * Therefore, we do not need to set a lock here. 
						 */
						
						System.err.format("New repository %s has been registered from %s.\n", name.toFile().getName(), fullpath.toFile().toString());
					} else if (kind == ENTRY_DELETE) {
						// repository has been removed
						repoPaths.remove(name.toFile().getName());
						/*
						 * concerning locking: see comment above in the ENTRY_CREATE case.
						 */
						System.err.format("Repository %s has been deregistered.\n", name.toFile().getName());
					} else if (kind == ENTRY_MODIFY) {
						// modification?
						// TODO what does this mean? Change of attributes / change of date time stamp or what?
						/* Analysis: change within the directory does not trigger a change of
						 * the directory itself - at least on Windows 
						 */
						System.err.format("Repository %s has been modified.\n", name.toFile().getName());
					}
				}	
				
				key.reset();
				// TODO if returned false, the baseDir has been deleted in the meantime => argl! --> error handling required
			}
		}
		
	}
	
	public void shutdown() {
		this.watchThread.interrupt();
	}

	private void initialize() {
		// retrieve initial state in the baseDir directory
		// and pre-fill the repoPaths
		for (File subDir : this.baseDir.listFiles()) {
			System.err.format("Repository %s has been initialized from %s.\n", subDir.getName(), subDir.toString());
			this.repoPaths.put(subDir.getName(), subDir);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		
		// remove the registration for the baseDir
		if (this.watchKey != null) {
			this.watchKey.cancel();
		}
	}

	public File getRepository(String name) {
		return this.repoPaths.get(name);
	}
}
