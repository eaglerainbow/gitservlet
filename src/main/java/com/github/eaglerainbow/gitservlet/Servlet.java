package com.github.eaglerainbow.gitservlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
	
	private static HashMap<String, String> repoPaths;
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Servlet() {
        super();
        repoPaths = new HashMap<String, String>();
        repoPaths.put("repo", "E:\\repo");
    }

    class Location {
    	public String repo;
    	public String ref;
    	public String file;
    }
    
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
		String path = request.getRequestURI().substring(request.getContextPath().length());
		
		Location loc = this.determineLocation(path);
		if (loc == null) {
			response.setStatus(500);
			response.getWriter().println("Invalid Path specified");
			return;
		}
		
		response.setHeader("X-debug-repo", loc.repo);
		response.setHeader("X-debug-ref", loc.ref);
		response.setHeader("X-debug-path", loc.file);
		
		String gitPath = repoPaths.get(loc.repo);
		if (gitPath == null) {
			response.setStatus(500);
			response.getWriter().println("Unknown repository specified");
			return;
		}
		Git repo = Git.open(new File(gitPath));
		Ref ref = repo.getRepository().getRef(loc.ref);
		String commitid = ref.getObjectId().getName();
		
		response.setHeader("X-debug-commitid", commitid);

		TreeWalk treeWalk = new TreeWalk(repo.getRepository());
		RevWalk rWalk = null;
		try {
			rWalk = new RevWalk(repo.getRepository());
			RevTree tree = rWalk.parseTree(ref.getObjectId());
			treeWalk.addTree(tree);
			
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
		response.addHeader("X-debug-objectid", treeWalk.getObjectId(0).getName());
		
		ObjectLoader loader = repo.getRepository().open(treeWalk.getObjectId(0));
		
		response.setContentLengthLong(loader.getSize());
		ServletOutputStream sos = response.getOutputStream();
		
		byte[] data = loader.getBytes();
		sos.write(data);
	}

}
