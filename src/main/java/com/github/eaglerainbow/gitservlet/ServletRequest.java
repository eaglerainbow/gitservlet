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

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

// TODO How to do Unit testing?
public class ServletRequest {
	private final String path;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final RepoBase repoBase;
	private final Log log;
	
	// TODO: This approach does not support namespaced tags and/or branches => might be a requirement
	private static final Pattern LOCATION_FROM_URL = Pattern.compile("^/([^/]*)/([^/]*)/(.*)");
	private boolean isDebug;    
	
	private class Location {
    	/**
    	 * internal name of the repository
    	 */
    	public String repo; 
    	
    	/**
    	 * the name of the reference within the git repository (i.e. "master" or "v1.0")
    	 */
    	public String ref;
    	
    	/**
    	 * the path and the filename itself within the commit referenced
    	 * by the reference in the given repository ("the file you want to have")
    	 */
    	public String file;
    }
	
    public ServletRequest(Log genericLog, String path, HttpServletRequest request, HttpServletResponse response, RepoBase repoBase) {
    	this.log = genericLog.deriveSpecificLog(this.getClass());
    	this.path = path;
    	this.request = request;
    	this.response = response;
    	this.repoBase = repoBase;
    	
		this.isDebug = "true".equals(this.request.getParameter("gitservlet-debug"));
		// TODO: Bad approach: needs to be sured by some authorization schema; 
		// with the current method, every productive user could gain internal server information
		
		if (this.isDebug) {
			this.log.fine("Debugging has been enabled");
		}
	}

	/**
     * Parses the given URLs and retrieves the corresponding location information of the file
     * @param urlpath the URL which shall be checked
     * @return the location information, or <code>null</code> if it cannot be determined
     */
    private Location determineLocation(String urlpath) {
    	Location loc = new Location();
    	
    	Matcher m = LOCATION_FROM_URL.matcher(urlpath);
    	if (!m.matches()) {
    		return null;
    	}
    	
    	loc.repo = m.group(1);
    	loc.ref = m.group(2);
    	loc.file = m.group(3);
    	
    	return loc;
    }
	
    /**
     * adds a Header information to the HTTP request, in case that debugging is switched on
     * @param name the name of the header parameter which shall be created ("X-debug-" is prefixed automatically by this routine)
     * @param value the value of the parameter which shall be sent
     */
    private void addDebugHeader(String name, String value) {
    	if (!this.isDebug)
    		// if debugging is not switched on, ignore
    		return;
    	
    	this.response.setHeader("X-debug-"+name, value);
    }
    
	public void process() throws IOException, LocalInternalServerException {
		this.log.fine(String.format("Processing path request for %s", this.path));
		
		Location loc = this.determineLocation(this.path);
		if (loc == null) {
			throw new LocalInternalServerException("Invalid Path specified");
		}
		
		this.addDebugHeader("repo", loc.repo);
		this.addDebugHeader("ref", loc.ref);
		this.addDebugHeader("path", loc.file);
		this.log.fine(String.format("Request: Repository: %s, Reference: %s, Filepath: %s", loc.repo, loc.ref, loc.file));
		
		// determine the path where the git repository is stored
		File gitPath = this.repoBase.getRepository(loc.repo);
		if (gitPath == null) {
			throw new LocalInternalServerException("Unknown repository specified");
		}
		this.log.fine(String.format("Repository is located at %s", gitPath.toString()));
		
		// load the git repository with JGit
		Git git = Git.open(gitPath);
		Repository repo = git.getRepository();
		// TODO The very first call to this method takes ages (what's the library doing there?
		// and how can we "prepone" this activity such that reply times are better right from the beginning?)

		// TODO can we cache the access to the repository, such that we only need to open it once?
		// How about concurrency and locking? Check the JGit API
		
		// resolve the given reference within this git repository
		Ref ref = repo.getRef(loc.ref);
		if (ref == null) { 
			throw new LocalInternalServerException("Specified reference could not be found / invalid reference");
		}
		
		// determine the Commit ID, which is behind that reference
		ObjectId commitoid = ref.getObjectId();
		String commitid = commitoid.getName();
		this.log.fine(String.format("Commit ID behind ref: %s", commitid));
		
		this.addDebugHeader("commitid", commitid);

		ObjectId fileoid = this.getFileObjectIdInCommit(repo, commitoid, loc.file);
		this.log.fine(String.format("File object ID: %s", fileoid.getName()));
		this.addDebugHeader("objectid", fileoid.getName());
		
		this.sendFile(repo, fileoid);
	}

	private void sendFile(Repository repo, ObjectId fileoid) throws IOException {
		// retrieve the Object from the Git repository
		ObjectLoader loader = repo.open(fileoid);
		
		// determine the length of the object which is requested
		long size = loader.getSize();
		this.log.fine(String.format("File size to transfer: %d", size));
		this.response.setContentLengthLong(size);
		ServletOutputStream sos = this.response.getOutputStream();
		
		// copy the bytes from the git repository to the output stream of this servlet
		loader.copyTo(sos);
		
		this.log.fine("Transfer is completed");
	}

	private ObjectId getFileObjectIdInCommit(Repository repo, ObjectId commitoid, String filename) throws LocalInternalServerException, IOException {
		/* TODO All this tree walker things may become quite expensive
		 * Clients tend to request multiple files within the same 
		 * tree.
		 * A better alternative would be to only do the tree walking once,
		 * reading all files into a Map<ConcatOfCommitIdAndPath, ObjectId>
		 * such that with the second request, we can directly get the
		 * ObjectId of the file without a need for treewalking.  
		 */
		this.log.fine(String.format("searching for file named %s in commit %s", filename, commitoid.getName()));
		// Prepare to search for the files within this commit
		TreeWalk treeWalk = new TreeWalk(repo);
		RevWalk rWalk = null;
		try {
			rWalk = new RevWalk(repo);
			// determine the tree out of the commit which we just retrieved out of the reference
			RevTree tree = rWalk.parseTree(commitoid);
			treeWalk.addTree(tree);
			
			// limit the search to only that single file, which the URL requests.
			treeWalk.setFilter(PathFilter.create(filename));
			// we know that there can only be one single file (if at all); that's why we don't need a loop here
			if (!treeWalk.next()) {
				this.log.info("File could not be found in the commit");
				throw new LocalInternalServerException("File could not be found for this reference");
			}
			
		} finally {
			rWalk.close();
			treeWalk.close();
		}
		ObjectId foid = treeWalk.getObjectId(0); 
		this.log.fine(String.format("File has Git Object Id: %s", foid.getName()));
		return foid;
	}
}
