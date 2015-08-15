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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class Servlet
 */
public class Servlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    
	private final RepoBase repoBase;
	private final Log genericLog;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Servlet() {
        super();
        
        this.genericLog = new Log(this);
        this.repoBase = new RepoBase(new File("E:\\repobase"));
        // TODO remove hard-coded location of repobase here (=> command line parameter?)
    }
    
	@Override
	public void destroy() {
		if (this.repoBase != null) {
			this.repoBase.shutdown();
			
			try {
				Thread.sleep(500); // allows the thread to really stop
			} catch (InterruptedException e) {
			} 
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// retrieve the URL which is requested
		String path = request.getRequestURI().substring(request.getContextPath().length());
		
		ServletRequest sr = new ServletRequest(this.genericLog, path, request, response, this.repoBase);
		try {
			sr.process();
		} catch (LocalInternalServerException lise) {
			response.setStatus(500);
			response.getWriter().println(lise.getMessage());
		}
	}

}
