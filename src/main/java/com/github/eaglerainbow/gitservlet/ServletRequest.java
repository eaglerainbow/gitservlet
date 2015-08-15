package com.github.eaglerainbow.gitservlet;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class ServletRequest {
	private final String path;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final RepoBase repoBase;
	
	private static final Pattern LOCATION_FROM_URL = Pattern.compile("^/([^/]*)/([^/]*)/(.*)");
	private boolean isDebug;    
	// TODO: This approach does not support namespaced tags and/or branches => might be a requirement
	
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
	
    public ServletRequest(String path, HttpServletRequest request, HttpServletResponse response, RepoBase repoBase) {
    	this.path = path;
    	this.request = request;
    	this.response = response;
    	this.repoBase = repoBase;
    	
		this.isDebug = "true".equals(this.request.getParameter("gitservlet-debug"));
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
    
	public void process() throws IOException {
		
		Location loc = this.determineLocation(path);
		if (loc == null) {
			this.response.setStatus(500);
			this.response.getWriter().println("Invalid Path specified");
			return;
		}
		
		this.addDebugHeader("repo", loc.repo);
		this.addDebugHeader("ref", loc.ref);
		this.addDebugHeader("path", loc.file);
		
		// determine the path where the git repository is stored
		File gitPath = this.repoBase.getRepository(loc.repo);
		if (gitPath == null) {
			this.response.setStatus(500);
			this.response.getWriter().println("Unknown repository specified");
			return;
		}
		
		// load the git repository with JGit
		Git repo = Git.open(gitPath);
		
		// resolve the given reference within this git repository
		Ref ref = repo.getRepository().getRef(loc.ref);
		if (ref == null) { 
			this.response.setStatus(500);
			this.response.getWriter().println("Specified reference could not be found / invalid reference");
			return;
		}
		
		// determine the Commit ID, which is behind that reference 
		String commitid = ref.getObjectId().getName();
		
		this.addDebugHeader("commitid", commitid);

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
				this.response.setStatus(500);
				this.response.getWriter().println("File could not be found for this reference");
				return;
			}
			
		} finally {
			rWalk.close();
			treeWalk.close();
		}
		if (this.isDebug) {
			this.addDebugHeader("objectid", treeWalk.getObjectId(0).getName());
		}
		
		// retrieve the Object from the Git repository
		ObjectLoader loader = repo.getRepository().open(treeWalk.getObjectId(0));
		
		// determine the length of the object which is requested
		this.response.setContentLengthLong(loader.getSize());
		ServletOutputStream sos = this.response.getOutputStream();
		
		/* TODO: if the size of a file gets large, we would read the entire file here at once
		 * this could have a bad effect on the server's TCO, as this would occupy much
		 * main memory ==> better to use a packaged approach here...
		 */
		
		// copy the bytes from the git repository to the output stream of this servlet
		byte[] data = loader.getBytes();
		sos.write(data);
	}
}
