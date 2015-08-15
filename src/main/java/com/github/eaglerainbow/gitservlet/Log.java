package com.github.eaglerainbow.gitservlet;

import java.util.logging.Level;

import javax.servlet.GenericServlet;

public class Log implements Cloneable {
	private final GenericServlet servlet;
	private String currentArea;
	
	public Log(GenericServlet servlet) { 
		this.servlet = servlet;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		Log clonedLog = new Log(this.servlet);
		
		return clonedLog;
	}

	public Log deriveSpecificLog(@SuppressWarnings("rawtypes") Class c) {
		Log otherLog = null;
		try {
			otherLog = (Log) this.clone();
		} catch (CloneNotSupportedException e) {
			// may not happen
			return null;
		}
		
		otherLog.setArea(c.getName());
		return otherLog;
	}
	
	private void setArea(String area) {
		this.currentArea = area;
	}
	
	private void log(Level level, String area, String message, Throwable throwable) {
		this.servlet.log(String.format("[%8s] - %30s: %s", level.getName(), area, message), throwable);
	}
	
	public void log(Level level, String message, Throwable throwable) {
		this.log(level, this.currentArea, message, throwable);
	}
	
	public void warn(String message, Throwable throwable) {
		this.log(Level.WARNING, message, throwable);
	}
	
	public void info(String message, Throwable throwable) {
		this.log(Level.INFO, message, throwable);
	}
	
	public void severe(String message, Throwable throwable) {
		this.log(Level.SEVERE, message, throwable);
	}
	
	public void fine(String message, Throwable throwable) {
		this.log(Level.FINE, message, throwable);
	}
	
	public void warn(String message) {
		this.log(Level.WARNING, message, null);
	}
	
	public void info(String message) {
		this.log(Level.INFO, message, null);
	}
	
	public void severe(String message) {
		this.log(Level.SEVERE, message, null);
	}
	
	public void fine(String message) {
		this.log(Level.FINE, message, null);
	}
}
