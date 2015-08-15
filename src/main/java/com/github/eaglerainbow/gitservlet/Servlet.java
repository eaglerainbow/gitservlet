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

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Servlet implementation class Servlet
 */
public class Servlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    
	private static final Pattern LOCATION_FROM_URL = Pattern.compile("^/([^/]*)/([^/]*)/(.*)");    
	// TODO: This approach does not support namespaced tags and/or branches => might be a requirement
	
	private final RepoBase repoBase;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Servlet() {
        super();
        this.repoBase = new RepoBase(new File("E:\\repobase"));
        // TODO remove hard-coded location of repobase here (=> command line parameter?)
    }

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
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// retrieve the URL which is requested
		String path = request.getRequestURI().substring(request.getContextPath().length());
		
		boolean isDebug = "true".equals(request.getParameter("gitservlet-debug"));
		
		Location loc = this.determineLocation(path);
		if (loc == null) {
			response.setStatus(500);
			response.getWriter().println("Invalid Path specified");
			return;
		}
		
		if (isDebug) {
			response.setHeader("X-debug-repo", loc.repo);
			response.setHeader("X-debug-ref", loc.ref);
			response.setHeader("X-debug-path", loc.file);
		}
		
		// determine the path where the git repository is stored
		File gitPath = this.repoBase.getRepository(loc.repo);
		if (gitPath == null) {
			response.setStatus(500);
			response.getWriter().println("Unknown repository specified");
			return;
		}
		
		// load the git repository with JGit
		Git repo = Git.open(gitPath);
		
		// resolve the given reference within this git repository
		Ref ref = repo.getRepository().getRef(loc.ref);
		if (ref == null) { 
			response.setStatus(500);
			response.getWriter().println("Specified reference could not be found / invalid reference");
			return;
		}
		
		// determine the Commit ID, which is behind that reference 
		String commitid = ref.getObjectId().getName();
		
		if (isDebug) {
			response.setHeader("X-debug-commitid", commitid);
		}

		// Prepare to search for the files within this commit
		TreeWalk treeWalk = new TreeWalk(repo.getRepository());
		RevWalk rWalk = null;
		try {
			rWalk = new RevWalk(repo.getRepository());
			// determine the tree out of the commit which we just retrieved out of the reference
			RevTree tree = rWalk.parseTree(ref.getObjectId());
			treeWalk.addTree(tree);
			
			// limit the search to only that single file, which the URL requests.
			treeWalk.setFilter(PathFilter.create(loc.file));
			if (!treeWalk.next()) {
				response.setStatus(500);
				response.getWriter().println("File could not be found for this reference");
				return;
			}
			
		} finally {
			rWalk.close();
			treeWalk.close();
		}
		if (isDebug) {
			response.addHeader("X-debug-objectid", treeWalk.getObjectId(0).getName());
		}
		
		// retrieve the Object from the Git repository
		ObjectLoader loader = repo.getRepository().open(treeWalk.getObjectId(0));
		
		// determine the length of the object which is requested
		response.setContentLengthLong(loader.getSize());
		ServletOutputStream sos = response.getOutputStream();
		
		// copy the bytes from the git repository to the output stream of this servlet
		byte[] data = loader.getBytes();
		sos.write(data);
	}

}
